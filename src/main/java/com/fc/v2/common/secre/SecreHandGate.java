package com.fc.v2.common.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 清退移交过门判定核——「这一处」。
 *
 * <p>整批怎么进门、单行怎么定去向、同一个批次码撞上第二遍怎么交代、册首三数从哪里
 * 轧出，这四问只认这一处给出的答案；行没有绕过它自落册的路，也没有自改去向的路，
 * 哪儿都不摆那样的把手。清退移交行服务只跑腿，判路全在这里；旧档那一层的沉取也归
 * 这一处判，别处不得另起一轮。</p>
 *
 * <p>今年立的五桩：</p>
 * <ul>
 *   <li>行上带什么：一行挂两个数（应交几件、核收合格几件），两个数一并递来，缺哪一个
 *       旁人都不许替它补。认载体只认件码——拿题名去凑的、件码念得不全的，统统算没对上。
 *       行的去向只许未勾、收下、退回三个字样，落不进第四个。一册里同一件载体占两行
 *       不算稀罕，两行两本账，谁的去向都压不到对方身上。</li>
 *   <li>什么情形只退这一行：应交件数压过核收上限的、报成零的、报成负数的、件码空着的、
 *   件码长过那一把尺的，原路退回，缘故写在这一行上；别的行不受它带，为这一行把整册
 *   退回起点重报一遍不许多。单册行数越上限的，进门头一步就点出来，整册原样退走，
 *   库里不许留半行。</li>
 *   <li>同一个批次码再递一遍：只拿递来的行与库里已落的挨个照面，一行不多添、一行不少减，
 *   头一遍落成的去向动它不得；真要改，先办撤批，撤干净才谈得上重递。行次归系统管：
 *   进门按落库先后排上号，排定便不再挪。</li>
 *   <li>过完门要给交代：回执只留原报、落地、退回三个数，退的与落的合起来须等于原报；
 *   三个数一律回库一行一行数出来，屏上那份只是复述。一行都没收着的册，回执只写
 *   「空批」，不出收讫。</li>
 *   <li>旧的那一层：去向空悬连着满两个年度的行沉入旧档，现行名单不再列它，存量里照旧
 *   翻得着、一条不少。行上带出的题名与名录代号以册面为凭，报文里那串只作对照，
 *   对不上时以册面为准，回执把对不上的那几行指名报出。一册一次递齐、整册进门，
 *   不接逐行零着递；本级送交与上级调回两条来路进同一个门，同一个批次码进来两次
 *   不会再另开一册。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component
public class SecreHandGate {

    /** 行落地情形：未勾（去向空悬） */
    public static final int ST_PENDING = 0;
    /** 行落地情形：收下 */
    public static final int ST_ACCEPTED = 1;
    /** 行落地情形：退回 */
    public static final int ST_RETURNED = 2;

    /** 交回渠道：本级送交 */
    public static final String KIND_LOCAL = "本级送交";
    /** 交回渠道：上级调回 */
    public static final String KIND_ABOVE = "上级调回";

    /** 件码那一把尺（库栏 varchar(64)） */
    public static final int ITEM_CODE_MAX = 64;
    /** 单册行数上限的默数（处里划的，可再调） */
    public static final int DEFAULT_MAX_ROWS_PER_BOOK = 500;
    /** 当地那条核收上限的默数（处里划的，可再调） */
    public static final BigDecimal DEFAULT_ACCEPT_LIMIT = new BigDecimal("9999");

    @Autowired
    private TSecreHandRowMapper secreHandRowMapper;

    @Autowired
    private TSecreCarrierMapper secreCarrierMapper;

    /** 单册行数上限：进门头一步先点行数，越过它的整册原样退走 */
    private int maxRowsPerBook = DEFAULT_MAX_ROWS_PER_BOOK;

    /** 当地那条核收上限：应交件数压过它的，只退这一行 */
    private BigDecimal acceptLimit = DEFAULT_ACCEPT_LIMIT;

    /**
     * 整批进门：一册一次递齐，不接逐行零着递。
     *
     * <p>进门头一步先点行数，越过单册上限的整册原样退走，库里不许留半行；行数够了
     * 就逐行判、逐行落，哪一行立不住只退哪一行，缘故写在这一行上，别的行不受它带。
     * 同一个批次码再递一遍，这一趟只照面不翻账。三数回库一行一行数出来，没落成的
     * 那几行把纸面行次随回执带出去。</p>
     *
     * @param batchNo 清退移交批次码（空着门都不让进：守不了「第二遍只照面」）
     * @param rows    递来的一册（按季度名册的行次序合成，行不许游到别册去）
     * @return 回执（三个数回库数出；收讫／空批／整册退回／照面）
     */
    @Transactional
    public SecreHandReceipt enter(String batchNo, List<SecreHandIn> rows) {
        // 批次码空着：这一册没法守「同一个批次码再递一遍只照面」，门都不让进
        if (StringUtils.isEmpty(batchNo) || batchNo.trim().isEmpty()) {
            return refuse(batchNo, rows == null ? 0 : rows.size(),
                    "批次码空着，守不了「同一个批次码再递一遍只照面」，整册原样退走");
        }
        // 空册：一行也没递着，回执只写空批
        if (rows == null || rows.isEmpty()) {
            SecreHandReceipt receipt = freshReceipt(batchNo);
            receipt.setOutcome(SecreHandReceipt.OUTCOME_EMPTY);
            receipt.setConsistent(true);
            return receipt;
        }
        // 同一个批次码再递一遍：只照面，不翻账，头一遍落成的去向动它不得
        List<TSecreHandRow> stored = findBatchRows(batchNo);
        if (!stored.isEmpty()) {
            return faceOff(batchNo, rows, stored);
        }
        // 进门头一步先点行数：越过上限的整册原样退走，库里不许留半行
        if (rows.size() > maxRowsPerBook) {
            return refuse(batchNo, rows.size(),
                    "原报" + rows.size() + "行，越过单册行数上限" + maxRowsPerBook
                            + "行，整册原样退走，库里不许留半行");
        }
        // 逐行判、逐行落：哪一行立不住只退哪一行，别的行不受它带
        int seq = 0;
        List<SecreHandReceipt.Mismatch> mismatches = new ArrayList<SecreHandReceipt.Mismatch>();
        for (SecreHandIn in : rows) {
            seq++;
            TSecreCarrier register = in == null ? null : findCarrier(in.getItemCode());
            String reason = judgeRow(in, register, acceptLimit);
            TSecreHandRow row = new TSecreHandRow();
            row.setBatchNo(batchNo);
            // 行次归系统管：进门按落库先后排上号，排定便不再挪
            row.setRowNo(seq);
            if (in != null) {
                row.setItemCode(in.getItemCode());
                row.setQty(in.getQty());
                row.setQualiNum(in.getQualiNum());
                row.setHandKind(in.getHandKind());
            }
            row.setStatus(reason == null ? ST_PENDING : ST_RETURNED);
            row.setArchFlag(0);
            row.setDelFlag(0);
            // 退这一行，缘故写在这一行上
            row.setRemark(reason);
            insertRow(row);
            if (reason == null) {
                // 报文里那串题名与名录代号只作对照，对不上时以册面为准，回执指名报出
                collectMismatch(seq, in, register, mismatches);
            }
        }
        // 三数回库一行一行数出来；没落成的那几行纸面行次随回执带出去
        SecreHandReceipt receipt = receiptOf(batchNo);
        receipt.setMismatches(mismatches);
        return receipt;
    }

    /**
     * 回执三数：回库一行一行数出来。
     *
     * <p>屏上先亮的那份不作数，只是这一份的复述；两处是同一回算的，才谈得上一致。
     * 没落成的那几行把纸面行次随回执带出去，好让机关那本册子行行对得上。</p>
     *
     * @param batchNo 批次码
     * @return 回执（原报、落地、退回三个数；退的与落的合起来须等于原报）
     */
    public SecreHandReceipt receiptOf(String batchNo) {
        SecreHandReceipt receipt = freshReceipt(batchNo);
        List<TSecreHandRow> stored = findBatchRows(batchNo);
        int landed = 0;
        int returned = 0;
        Set<Integer> seenRowNo = new HashSet<Integer>();
        List<String> offRows = new ArrayList<String>();
        List<SecreHandReceipt.RowNote> returnedRows = new ArrayList<SecreHandReceipt.RowNote>();
        for (TSecreHandRow row : stored) {
            Integer status = row.getStatus();
            if (status != null && (status == ST_PENDING || status == ST_ACCEPTED)) {
                landed++;
            } else if (status != null && status == ST_RETURNED) {
                returned++;
                returnedRows.add(new SecreHandReceipt.RowNote(
                        row.getRowNo() == null ? -1 : row.getRowNo(), row.getItemCode(), row.getRemark()));
            } else {
                // 去向落不进第四个：对不住的那一行点名报出去
                offRows.add("第" + row.getRowNo() + "行：去向落不进三个字样（"
                        + (status == null ? "空着" : status) + "）");
            }
            Integer rowNo = row.getRowNo();
            if (rowNo == null) {
                offRows.add("有一行行次空着（件码 " + row.getItemCode() + "）");
            } else if (!seenRowNo.add(rowNo)) {
                offRows.add("第" + rowNo + "行：行次重了号");
            }
        }
        receipt.setReported(stored.size());
        receipt.setLanded(landed);
        receipt.setReturned(returned);
        receipt.setReturnedRows(returnedRows);
        // 一行都没收着的册只写空批，不出收讫；收讫是有行数的册才说的话
        receipt.setOutcome(landed > 0 ? SecreHandReceipt.OUTCOME_ACCEPT : SecreHandReceipt.OUTCOME_EMPTY);
        receipt.setOffRows(offRows);
        receipt.setConsistent(offRows.isEmpty() && receipt.isBalanced());
        return receipt;
    }

    /**
     * 随手点一行去核：它行上的去向与回执三个数互证互对。
     *
     * <p>与回执三数是同一回算的——回库一行一行数出来，对不住的那几行点名报出去，
     * 「差不离」三个字不作交代。</p>
     *
     * @param batchNo 批次码
     * @return 回执（带着对不住的那几行点名）
     */
    public SecreHandReceipt verify(String batchNo) {
        return receiptOf(batchNo);
    }

    /**
     * 单行定去向：未勾的行勾成收下或退回。
     *
     * <p>去向只许收下、退回两个字样，落不进第四个；头一遍落成的去向动它不得——
     * 真要改，先办撤批，撤干净才谈得上重递。收下头一行不捎带第二行，退回第二行
     * 也不牵扯头一行，谁的去向都压不到对方身上。已沉旧档的行，现行名单里定不了
     * 去向。</p>
     *
     * @param batchNo  批次码
     * @param rowNo    纸面行次（进门排上的号）
     * @param decision 去向：{@link #ST_ACCEPTED} 收下 或 {@link #ST_RETURNED} 退回
     * @param reason   退这一行的缘故（写在这一行上；收下可空）
     * @return 判定结果
     */
    public SecreHandVerdict decideRow(String batchNo, Integer rowNo, int decision, String reason) {
        if (decision != ST_ACCEPTED && decision != ST_RETURNED) {
            return SecreHandVerdict.fail("decision", "去向只许收下、退回两个字样，落不进第四个");
        }
        if (rowNo == null) {
            return SecreHandVerdict.fail("rowNo", "行次空着，点不出是哪一行");
        }
        TSecreHandRow row = findBatchRow(batchNo, rowNo);
        if (row == null) {
            return SecreHandVerdict.fail("rowNo", "第" + rowNo + "行库里没有");
        }
        if (row.getArchFlag() != null && row.getArchFlag() == 1) {
            return SecreHandVerdict.fail("rowNo", "第" + rowNo + "行已沉旧档，现行名单里定不了去向");
        }
        if (row.getStatus() == null || row.getStatus() != ST_PENDING) {
            return SecreHandVerdict.fail("status", "第" + rowNo + "行头一遍落成的去向（"
                    + statusText(row.getStatus()) + "）动它不得；真要改，先办撤批，撤干净才谈得上重递");
        }
        if (decision == ST_RETURNED && (reason == null || reason.trim().isEmpty())) {
            return SecreHandVerdict.fail("decision", "退回这一行，缘故得写在这一行上");
        }
        TSecreHandRow upd = new TSecreHandRow();
        upd.setId(row.getId());
        upd.setStatus(decision);
        if (reason != null && !reason.trim().isEmpty()) {
            upd.setRemark(reason);
        }
        updateRow(upd);
        return SecreHandVerdict.ok("第" + rowNo + "行定去向：" + statusText(decision));
    }

    /**
     * 撤批：这一册从头撤干净，才谈得上重递。
     *
     * <p>真要改同一个批次码落成的账，只有这一条道：先办撤批，撤干净再整册重递。
     * 撤批不抹纸面——这一册的行从现行账上摘下（删除标记落 1），库底仍翻得着。</p>
     *
     * @param batchNo 批次码
     * @return 撤下几行
     */
    public int cancelBatch(String batchNo) {
        if (StringUtils.isEmpty(batchNo) || batchNo.trim().isEmpty()) {
            return 0;
        }
        return softDeleteBatch(batchNo);
    }

    /**
     * 旧档沉取：去向空悬连着满两个年度还没着落的行，沉入旧档那一层。
     *
     * <p>沉下去的现行名单不再列它，存量里先前收过的照旧翻得着、一条不少。这条路
     * 只朝旧册开，往后的新册走不到它跟前。沉取也归这一处判，别处不得另起一轮。</p>
     *
     * @param today 起算那一日（连着满两个年度：行落库那一年的次一年元旦前满两年）
     * @return 这一回沉下去几行
     */
    public int sinkStale(LocalDate today) {
        if (today == null) {
            return 0;
        }
        // 连着满两个年度：落库那一年的次一年元旦为界，界前的未勾行沉下去
        LocalDate cutoff = LocalDate.of(today.getYear() - 2, 1, 1);
        return markSunk(cutoff);
    }

    /**
     * 旧档沉取（以今日起算）。
     *
     * @return 这一回沉下去几行
     */
    public int sinkStale() {
        return sinkStale(LocalDate.now());
    }

    /**
     * 现行名单：这一册还在现行这一层的行（按纸面行次排）。
     *
     * @param batchNo 批次码
     * @return 现行那几行
     */
    public List<TSecreHandRow> listCurrent(String batchNo) {
        return listRows(batchNo, 0);
    }

    /**
     * 旧档那一层：这一册已沉下去的行（按纸面行次排，一条不少）。
     *
     * @param batchNo 批次码
     * @return 旧档那几行
     */
    public List<TSecreHandRow> listArchive(String batchNo) {
        return listRows(batchNo, 1);
    }

    /**
     * 同一批次码再递一遍：拿递来的行与库里已落的挨个照面。
     *
     * <p>一行不多添、一行不少减，头一遍落成的去向动它不得；三数照旧回库数出来。
     * 对得上几行、对不上几行、多递几行、少递几行，挨个指名。</p>
     */
    private SecreHandReceipt faceOff(String batchNo, List<SecreHandIn> incoming, List<TSecreHandRow> stored) {
        SecreHandReceipt receipt = receiptOf(batchNo);
        receipt.setOutcome(SecreHandReceipt.OUTCOME_FACE);
        Map<Integer, TSecreHandRow> byRowNo = new LinkedHashMap<Integer, TSecreHandRow>();
        for (TSecreHandRow row : stored) {
            byRowNo.put(row.getRowNo(), row);
        }
        int total = Math.max(incoming.size(), stored.size());
        int same = 0;
        int diff = 0;
        int more = 0;
        int less = 0;
        List<SecreHandReceipt.FaceOff> faceOffs = new ArrayList<SecreHandReceipt.FaceOff>();
        for (int i = 1; i <= total; i++) {
            SecreHandIn in = i <= incoming.size() ? incoming.get(i - 1) : null;
            TSecreHandRow db = byRowNo.get(i);
            if (in == null && db != null) {
                less++;
                faceOffs.add(new SecreHandReceipt.FaceOff(i,
                        "少递：库里第" + i + "行（件码 " + db.getItemCode() + "）这趟没递来"));
                continue;
            }
            if (db == null) {
                more++;
                faceOffs.add(new SecreHandReceipt.FaceOff(i,
                        "多递：这趟第" + i + "行（件码 " + (in == null ? null : in.getItemCode()) + "）库里没有"));
                continue;
            }
            List<String> cols = new ArrayList<String>();
            if (!Objects.equals(in.getItemCode(), db.getItemCode())) {
                cols.add("件码");
            }
            if (compareNum(in.getQty(), db.getQty()) != 0) {
                cols.add("应交件数");
            }
            if (compareNum(in.getQualiNum(), db.getQualiNum()) != 0) {
                cols.add("核收合格件数");
            }
            if (!Objects.equals(in.getHandKind(), db.getHandKind())) {
                cols.add("交回渠道");
            }
            if (cols.isEmpty()) {
                same++;
                faceOffs.add(new SecreHandReceipt.FaceOff(i, "对得上"));
            } else {
                diff++;
                faceOffs.add(new SecreHandReceipt.FaceOff(i, "对不上：" + joinCols(cols) + "两本账"));
            }
        }
        receipt.setFaceOffs(faceOffs);
        receipt.setNote("同一批次码再递一遍：只照面不翻账，库里" + stored.size()
                + "行去向一律没动（对得上" + same + "行，对不上" + diff + "行，多递" + more
                + "行，少递" + less + "行）；真要改，先办撤批，撤干净才谈得上重递");
        return receipt;
    }

    /**
     * 单行立不立得住：立不住时回退这一行的缘故，立得住回 null。
     *
     * <p>只认桩上钉的那几条：件码空着的、件码长过那一把尺的、两个数缺报的（旁人不许
     * 替它补）、应交报成零的、报成负数的、压过核收上限的、件码与册面对不上的。
     * 旁处不许自己揣度再添名目。</p>
     */
    private static String judgeRow(SecreHandIn in, TSecreCarrier register, BigDecimal acceptLimit) {
        if (in == null) {
            return "这一行空着递来";
        }
        String code = in.getItemCode();
        if (code == null || code.trim().isEmpty()) {
            return "件码空着";
        }
        if (code.length() > ITEM_CODE_MAX) {
            return "件码长过那一把尺（" + ITEM_CODE_MAX + "字）";
        }
        BigDecimal qty = in.getQty();
        if (qty == null) {
            return "应交件数缺报，旁人不许替它补";
        }
        if (in.getQualiNum() == null) {
            return "核收合格件数缺报，旁人不许替它补";
        }
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return "应交件数报成零";
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            return "应交件数报成负数";
        }
        if (acceptLimit != null && qty.compareTo(acceptLimit) > 0) {
            return "应交件数压过核收上限（" + acceptLimit.toPlainString() + "）";
        }
        if (register == null) {
            return "件码与册面对不上（认载体只认件码，拿题名去凑不算）";
        }
        return null;
    }

    /**
     * 题名与名录代号对照：报文里那串只作对照，两处对不上时以册面为准，指名报出。
     */
    private void collectMismatch(int rowNo, SecreHandIn in, TSecreCarrier register,
                                 List<SecreHandReceipt.Mismatch> mismatches) {
        List<String> parts = new ArrayList<String>();
        if (StringUtils.isNotEmpty(in.getTitle()) && !Objects.equals(in.getTitle(), register.getContent())) {
            parts.add("题名对不上：报文「" + in.getTitle() + "」，册面「"
                    + (register.getContent() == null ? "空着" : register.getContent()) + "」，以册面为准");
        }
        if (StringUtils.isNotEmpty(in.getBookNo()) && !Objects.equals(in.getBookNo(), register.getBookNo())) {
            parts.add("名录代号对不上：报文「" + in.getBookNo() + "」，册面「"
                    + (register.getBookNo() == null ? "空着" : register.getBookNo()) + "」，以册面为准");
        }
        if (!parts.isEmpty()) {
            mismatches.add(new SecreHandReceipt.Mismatch(rowNo, in.getItemCode(), joinParts(parts)));
        }
    }

    /** 整册原样退走的回执：库里不许留半行，三个数照原报交代 */
    private SecreHandReceipt refuse(String batchNo, int reported, String note) {
        SecreHandReceipt receipt = freshReceipt(batchNo);
        receipt.setOutcome(SecreHandReceipt.OUTCOME_REFUSED);
        receipt.setReported(reported);
        receipt.setLanded(0);
        receipt.setReturned(reported);
        receipt.setNote(note);
        receipt.setConsistent(true);
        return receipt;
    }

    /** 空白回执：三个数从零起，交代待落 */
    private SecreHandReceipt freshReceipt(String batchNo) {
        SecreHandReceipt receipt = new SecreHandReceipt();
        receipt.setBatchNo(batchNo);
        return receipt;
    }

    /** 两个数对不对得上（空对空算对得上） */
    private static int compareNum(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    /** 去向说成人话 */
    public static String statusText(Integer status) {
        if (status == null) {
            return "空悬";
        }
        if (status == ST_PENDING) {
            return "未勾";
        }
        if (status == ST_ACCEPTED) {
            return "收下";
        }
        if (status == ST_RETURNED) {
            return "退回";
        }
        return "落不进三个字样（" + status + "）";
    }

    private static String joinCols(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(cols.get(i));
        }
        return sb.toString();
    }

    private static String joinParts(List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append("；");
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public int getMaxRowsPerBook() {
        return maxRowsPerBook;
    }

    public void setMaxRowsPerBook(int maxRowsPerBook) {
        this.maxRowsPerBook = maxRowsPerBook;
    }

    public BigDecimal getAcceptLimit() {
        return acceptLimit;
    }

    public void setAcceptLimit(BigDecimal acceptLimit) {
        this.acceptLimit = acceptLimit;
    }

    // ---------------- 跑腿层（库口）：判路全在上头，这里只照单跑腿 ----------------

    /** 这一册落在库里的全部行（含已沉旧档的，撤批摘下的不算），按纸面行次排 */
    protected List<TSecreHandRow> findBatchRows(String batchNo) {
        return secreHandRowMapper.selectList(new QueryWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .orderByAsc("row_no"));
    }

    /** 这一册里纸面行次为 rowNo 的那一行 */
    protected TSecreHandRow findBatchRow(String batchNo, Integer rowNo) {
        return secreHandRowMapper.selectOne(new QueryWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo)
                .eq("row_no", rowNo)
                .eq("del_flag", 0));
    }

    /** 认载体只认件码：走件码对册面，对不上为 null */
    protected TSecreCarrier findCarrier(String itemCode) {
        if (itemCode == null || itemCode.trim().isEmpty()) {
            return null;
        }
        return secreCarrierMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("carrier_no", itemCode)
                .eq("del_flag", 0));
    }

    /** 落一行 */
    protected int insertRow(TSecreHandRow row) {
        return secreHandRowMapper.insert(row);
    }

    /** 改一行（按主键，只动带过来的栏） */
    protected int updateRow(TSecreHandRow row) {
        return secreHandRowMapper.updateById(row);
    }

    /** 撤批：这一册的行从现行账上摘下（删除标记落 1），库底仍翻得着 */
    protected int softDeleteBatch(String batchNo) {
        TSecreHandRow upd = new TSecreHandRow();
        upd.setDelFlag(1);
        upd.setUpdateTime(new Date());
        return secreHandRowMapper.update(upd, new UpdateWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0));
    }

    /** 旧档沉取：界前落库、去向仍空悬的现行行，沉到旧档那一层 */
    protected int markSunk(LocalDate cutoff) {
        TSecreHandRow upd = new TSecreHandRow();
        upd.setArchFlag(1);
        upd.setUpdateTime(new Date());
        return secreHandRowMapper.update(upd, new UpdateWrapper<TSecreHandRow>()
                .eq("del_flag", 0)
                .eq("arch_flag", 0)
                .eq("status", ST_PENDING)
                .lt("create_time", Timestamp.valueOf(cutoff.atStartOfDay())));
    }

    /** 现行名单或旧档那一层（按纸面行次排） */
    protected List<TSecreHandRow> listRows(String batchNo, int archFlag) {
        return secreHandRowMapper.selectList(new QueryWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0)
                .eq("arch_flag", archFlag)
                .orderByAsc("row_no"));
    }
}
