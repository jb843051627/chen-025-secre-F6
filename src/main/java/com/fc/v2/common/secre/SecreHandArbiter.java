package com.fc.v2.common.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

/**
 * 清退移交判定核——「这一处」。
 *
 * <p>整批怎么进门、单行怎么定去向、同一个批次码撞上第二遍怎么交代、册首三数从哪里
 * 轧出、旧档那一层的沉取——这五问只认这一处给出的答案。行没有绕过它自落册的路，
 * 也没有自改去向的路；「第二遍只照面不翻账」这句话也只在这一处有解。</p>
 *
 * <p>五桩：</p>
 * <ul>
 *   <li>一行挂两个数：应交几件、核收合格几件，一并递来，缺哪一个旁人都不许替它补；
 *       认载体只认件码，拿题名凑、件码念不全的统统算没对上。行的去向只许三个字样
 *       （未勾／收下／退回），由这一处定，报文说了不算。同一件码占两行是两本账，
 *       谁的去向都压不到对方身上。</li>
 *   <li>应交件数压过当地核收上限、报成零、报成负数、件码空着、件码长过尺的，原路
 *       退回这一行，缘故写在这一行上，别的行不受带，更不许为它把整册退回重报。
 *       越过单册行数上限的，整册原样退走，库里不留半行。</li>
 *   <li>同一个批次码再递一遍，只拿递来的行与库里已落的挨个照面：一行不多添、一行
 *       不少减，头一遍的去向动不得；要改先撤批，撤干净才谈得上重递。行次按落库
 *       先后排定，排定不再挪，退回去的行隔日查仍是原行次。</li>
 *   <li>回执三数（原报、落地、原路退回）一律回库逐行数出来，屏上那份只是复述；
 *       退的与落的合起来须等于原报。没落成的行也把纸面行次随回执带出去。
 *       一行都没收着的只写「空批」，不出收讫。</li>
 *   <li>去向一直空悬、连着满两个年度还没着落的行，沉入旧档那一层，现行名单不再列；
 *       先前收下的照旧翻得着、一条不少；这条路只朝旧册开，新册走不到它跟前。
 *       行一律由载体册面那一行关联生成，题名与名录代号以册面为凭，报文那一串只作
 *       对照，对不上以册面为准并逐行点名。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component
public class SecreHandArbiter {

    /** 行的去向：未勾（空悬） */
    public static final int STATUS_PENDING = 0;
    /** 行的去向：收下 */
    public static final int STATUS_RECEIVED = 1;
    /** 行的去向：退回 */
    public static final int STATUS_RETURNED = 2;

    /** 旧档层：现行名单 */
    public static final int ARCHIVE_CURRENT = 0;
    /** 旧档层：已满两个年度没着落，沉入旧档 */
    public static final int ARCHIVE_OLD = 1;

    /** 来路：本级送交 */
    public static final String HAND_KIND_LOCAL = "本级送交";
    /** 来路：上级调回 */
    public static final String HAND_KIND_ABOVE = "上级调回";

    /** 单册行数上限：进门头一步先点行数，越过上限整册原样退走 */
    public static final int MAX_ROWS_PER_BOOK = 500;

    /** 件码那一把尺：长过它的，原路退回（与库列 varchar(64) 对齐） */
    public static final int ITEM_CODE_MAX_LENGTH = 64;

    /** 当地单行核收上限：应交件数压过它的，原路退回这一行 */
    public static final BigDecimal QTY_MAX = new BigDecimal("9999.99");

    /** 原路退回缘故写在备注上的前缀（带回落在哪一栏） */
    public static final String REASON_PREFIX = "原路退回[";
    /** 报文与册面对不上的备注前缀 */
    public static final String MISMATCH_PREFIX = "报文对不上·";

    @Autowired
    private TSecreCarrierMapper secreCarrierMapper;

    /**
     * 进门头一步：点行数。越过单册上限的整册原样退走，库里不许留半行。
     *
     * @param rowCount 这一册递来几行
     * @return true 表示越限，整册不收
     */
    public boolean oversizeBook(int rowCount) {
        return rowCount > MAX_ROWS_PER_BOOK;
    }

    /**
     * 拿件码对册面：认载体只认件码，不拿题名凑。
     *
     * @param itemCode 载体件码
     * @return 册面上的那一行；对不上为 null
     */
    public TSecreCarrier findFace(String itemCode) {
        if (StringUtils.isBlank(itemCode)) {
            return null;
        }
        return secreCarrierMapper.selectOne(new QueryWrapper<TSecreCarrier>()
                .eq("carrier_no", itemCode)
                .eq("del_flag", 0));
    }

    /**
     * 单行判定：这一行收下还是原路退回。
     *
     * <p>应交、合格两个数一并递来，缺一不许补；应交为零、为负、压过核收上限，
     * 件码空着、超长、对不上册面的，原路退回这一行。收得下的，再把报文里随来的
     * 题名与名录代号跟册面对一遍：对不上不挡收，以册面为准，只逐条点名。</p>
     *
     * @param row 进门那一行
     * @return 判定结果
     */
    public SecreHandVerdict judgeRow(TSecreHandRow row) {
        if (row == null) {
            return SecreHandVerdict.fail("row", "这一行空着，原路退回");
        }
        // 应交件数、核收合格件数：一行挂两个数，缺一不可，旁人不许替它补
        BigDecimal qty = row.getQty();
        if (qty == null) {
            return SecreHandVerdict.fail("qty", "应交件数空着：两个数缺一不可，旁人不许替它补一个上去，原路退回这一行");
        }
        if (qty.compareTo(BigDecimal.ZERO) < 0) {
            return SecreHandVerdict.fail("qty", "应交件数报成负数，原路退回这一行");
        }
        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            return SecreHandVerdict.fail("qty", "应交件数报成零，原路退回这一行");
        }
        if (qty.compareTo(QTY_MAX) > 0) {
            return SecreHandVerdict.fail("qty", "应交件数压过了当地核收上限（" + QTY_MAX.toPlainString()
                    + "），原路退回这一行");
        }
        BigDecimal quali = row.getQualiNum();
        if (quali == null) {
            return SecreHandVerdict.fail("qualiNum", "核收合格件数空着：两个数缺一不可，旁人不许替它补一个上去，原路退回这一行");
        }
        if (quali.compareTo(BigDecimal.ZERO) < 0) {
            return SecreHandVerdict.fail("qualiNum", "核收合格件数报成负数，原路退回这一行");
        }
        if (quali.compareTo(qty) > 0) {
            return SecreHandVerdict.fail("qualiNum", "核收合格件数冒过了应交件数，原路退回这一行");
        }
        // 认载体只认件码
        String code = row.getItemCode();
        if (StringUtils.isBlank(code)) {
            return SecreHandVerdict.fail("itemCode", "件码空着，原路退回这一行");
        }
        if (code.trim().length() > ITEM_CODE_MAX_LENGTH) {
            return SecreHandVerdict.fail("itemCode", "件码长过了那一把尺（" + ITEM_CODE_MAX_LENGTH
                    + " 位），原路退回这一行");
        }
        TSecreCarrier face = findFace(code.trim());
        if (face == null) {
            // 拿题名去凑的、件码念得不全的，统统算没对上
            return SecreHandVerdict.fail("itemCode", "件码对不上载体册面（拿题名去凑、件码念得不全都算没对上），原路退回这一行");
        }
        SecreHandVerdict verdict = SecreHandVerdict.ok(face, compareFace(row, face));
        verdict.setRowNo(row.getRowNo());
        verdict.setItemCode(code.trim());
        return verdict;
    }

    /**
     * 报文随来的题名与名录代号跟册面对一遍。对不上不挡收、以册面为准，只逐条点名。
     * 报文那两栏空着的，不作对照、不点名。
     */
    public List<SecreHandMismatch> compareFace(TSecreHandRow row, TSecreCarrier face) {
        List<SecreHandMismatch> list = new ArrayList<SecreHandMismatch>();
        if (row == null || face == null) {
            return list;
        }
        Integer rowNo = row.getRowNo();
        String code = face.getCarrierNo();
        if (StringUtils.isNotBlank(row.getReportedContent())
                && !Objects.equals(row.getReportedContent().trim(), safe(face.getContent()))) {
            list.add(new SecreHandMismatch(rowNo, code, "content",
                    "题名对不上：报文为「" + row.getReportedContent().trim() + "」，册面为「"
                            + safe(face.getContent()) + "」，以册面为准"));
        }
        if (StringUtils.isNotBlank(row.getReportedBookNo())
                && !Objects.equals(row.getReportedBookNo().trim(), safe(face.getBookNo()))) {
            list.add(new SecreHandMismatch(rowNo, code, "bookNo",
                    "名录代号对不上：报文为「" + row.getReportedBookNo().trim() + "」，册面为「"
                            + safe(face.getBookNo()) + "」，以册面为准"));
        }
        return list;
    }

    /**
     * 把一条原路退回的缘故写成落在这一行备注上的话（退回只带这一行）。
     */
    public String writeReturnRemark(String field, String msg) {
        return REASON_PREFIX + field + "]：" + msg;
    }

    /** 把一条报文对不上的点名写成落在这一行备注上的话（行仍按册面收下）。 */
    public String writeMismatchRemark(SecreHandMismatch m) {
        String label = "bookNo".equals(m.getField()) ? "名录代号" : "题名";
        return MISMATCH_PREFIX + label + "：" + m.getMsg();
    }

    /**
     * 空悬满两个年度还没着落的行，沉入旧档那一层。
     *
     * <p>只认去向未勾（status=0）的现行行；已收下的旧账照旧翻得着，不沉。
     * 这条路只朝旧册开——新册的行年份不够，走不到它跟前。</p>
     *
     * @param row     要判的行
     * @param refYear 照面的那个年度（今岁）
     * @return true 表示该沉入旧档
     */
    public boolean shouldSink(TSecreHandRow row, int refYear) {
        if (row == null || row.getCreateTime() == null) {
            return false;
        }
        if (row.getStatus() == null || row.getStatus() != STATUS_PENDING) {
            return false;
        }
        if (row.getArchiveFlag() != null && row.getArchiveFlag() == ARCHIVE_OLD) {
            return false;
        }
        Calendar c = Calendar.getInstance();
        c.setTime(row.getCreateTime());
        return refYear - c.get(Calendar.YEAR) >= 2;
    }

    /**
     * 册首三数：拿库里这一批的行一行一行数出来，不是先亮在屏上的那份。
     *
     * <p>退的与落的合起来须等于原报，对不上就不给出收讫；落地为零（一册空递、
     * 或行行退回）只写空批。退回明细带原报行次，对不上的行从备注里逐条点回来。</p>
     *
     * @param batchNo 批次码
     * @param rows    回库点出来的这一批全部行（del_flag=0）
     * @return 回执
     */
    public SecreHandReceipt buildReceipt(String batchNo, List<TSecreHandRow> rows) {
        SecreHandReceipt receipt = new SecreHandReceipt();
        receipt.setBatchNo(batchNo);
        if (rows == null) {
            settleReceipt(receipt);
            return receipt;
        }
        // 按落库先后（行次）点：行次排定便不再挪
        List<TSecreHandRow> ordered = new ArrayList<TSecreHandRow>(rows);
        ordered.sort((a, b) -> {
            int na = a.getRowNo() == null ? Integer.MAX_VALUE : a.getRowNo();
            int nb = b.getRowNo() == null ? Integer.MAX_VALUE : b.getRowNo();
            return Integer.compare(na, nb);
        });
        int landed = 0;
        int returned = 0;
        for (TSecreHandRow r : ordered) {
            Integer status = r.getStatus();
            if (status != null && status == STATUS_RECEIVED) {
                landed++;
                receipt.getMismatches().addAll(readMismatchRemarks(r));
            } else if (status != null && status == STATUS_RETURNED) {
                returned++;
                receipt.getReturnedRows().add(readReturnRemark(r));
            }
        }
        receipt.setReported(ordered.size());
        receipt.setLanded(landed);
        receipt.setReturned(returned);
        settleReceipt(receipt);
        return receipt;
    }

    /** 三数轧平才谈得上一致；一行没收着只写空批，不出收讫。 */
    private void settleReceipt(SecreHandReceipt receipt) {
        receipt.setReceived(receipt.getLanded() > 0 && receipt.balances());
        receipt.setEmpty(receipt.getLanded() == 0);
    }

    /** 从这一行备注里把原路退回的缘故读回来（带落在哪一栏）。 */
    private SecreHandReturned readReturnRemark(TSecreHandRow r) {
        String field = "";
        String msg = r.getRemark();
        String remark = r.getRemark();
        if (remark != null && remark.startsWith(REASON_PREFIX)) {
            int end = remark.indexOf(']');
            if (end > REASON_PREFIX.length()) {
                field = remark.substring(REASON_PREFIX.length(), end);
                int cut = remark.indexOf('：', end);
                msg = cut >= 0 ? remark.substring(cut + 1) : remark.substring(end + 1);
            }
        }
        return new SecreHandReturned(r.getRowNo(), r.getItemCode(), field, msg);
    }

    /** 从这一行备注里把报文对不上的点名逐条读回来。 */
    private List<SecreHandMismatch> readMismatchRemarks(TSecreHandRow r) {
        List<SecreHandMismatch> list = new ArrayList<SecreHandMismatch>();
        String remark = r.getRemark();
        if (StringUtils.isEmpty(remark)) {
            return list;
        }
        for (String line : remark.split("；")) {
            String part = line.trim();
            if (!part.startsWith(MISMATCH_PREFIX)) {
                continue;
            }
            String field = part.contains("名录代号") ? "bookNo" : "content";
            list.add(new SecreHandMismatch(r.getRowNo(), r.getItemCode(), field,
                    part.substring(MISMATCH_PREFIX.length())));
        }
        return list;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
