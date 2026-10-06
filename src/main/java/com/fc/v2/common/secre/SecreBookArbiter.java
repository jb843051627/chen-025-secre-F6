package com.fc.v2.common.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 涉密载体入册判定核——「这一处」。
 *
 * <p>这一行立不立得住、还差几件轧出几、这一屏该见哪几行、两份名录打架时以哪一份
 * 作准，全认这一处给出的答案；旁处不许留第二处能绕过它的判路。册面服务、名录服务、
 * 旧入册口与同义新口，一律到这里来对答案。</p>
 *
 * <p>规矩：</p>
 * <ul>
 *   <li>还差几件只由这一行自己的两个数当场轧出来：应交减已收。报文里带了多少不作数。</li>
 *   <li>应交件数空着、写成负数、或小于同一行已收件数，这一行立不起来；已收件数冒过
 *       应交，同样立不起来；名录代号与册面对不上，也立不起来。打回的话点名毛病落在哪一栏。</li>
 *   <li>行与名录的关联一律走名录代号（文本），不拿名录主键挂行。</li>
 *   <li>名录已撤出只挡新挂（头一回入册、或改口换挂靠到它名下），不抹旧账。</li>
 *   <li>上级名录下发与本机关这份对不上时，以本机关这份作准；冲突逐条点名到代号，
 *       缘由落到本地名录那一份的备注里，不许悄悄盖掉。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Component
public class SecreBookArbiter {

    /** 名录情形：在册 */
    public static final int BOOK_STATUS_ON = 0;
    /** 名录情形：已撤出 */
    public static final int BOOK_STATUS_OFF = 1;

    /** 来路：上级名录下发 */
    public static final String SYNC_FROM_ABOVE = "上级名录下发";
    /** 来路：本机关自录 */
    public static final String SYNC_LOCAL = "本机关自录";

    @Autowired
    private TSecreOrgBookMapper secreOrgBookMapper;

    /**
     * 轧差：还差几件 = 应交减已收。
     *
     * <p>这一格不由人手填，也不由报文说了算，落库那一刻一律按这一行自己的
     * 两个数当场轧一遍。已收空着按零算。</p>
     *
     * @param needNum 应交件数
     * @param gotNum  已收件数
     * @return 还差几件
     */
    public Integer settleLack(Integer needNum, Integer gotNum) {
        int need = needNum == null ? 0 : needNum;
        int got = gotNum == null ? 0 : gotNum;
        return need - got;
    }

    /**
     * 走名录代号对册。代号是文本，关联一律走它，不拿名录主键挂行。
     *
     * @param bookNo 名录代号
     * @return 对上的名录；对不上为 null
     */
    public TSecreOrgBook findBook(String bookNo) {
        if (StringUtils.isEmpty(bookNo)) {
            return null;
        }
        return secreOrgBookMapper.selectOne(new QueryWrapper<TSecreOrgBook>()
                .eq("book_no", bookNo)
                .eq("del_flag", 0));
    }

    /**
     * 头一回入册的判定：这一行立不立得住。
     *
     * @param row 入册那一行
     * @return 判定结果；立得住时带着对上的名录与轧出来的差
     */
    public SecreVerdict judgeNewRow(TSecreCarrier row) {
        return judgeRow(row, true);
    }

    /**
     * 这一行立不立得住。
     *
     * <p>应交件数空着、写成负数、或小于同一行已收件数，立不起来；已收件数冒过应交，
     * 立不起来；名录代号空着或与册面对不上，立不起来。名录已撤出的，只挡新挂——
     * 头一回入册、或改口换挂靠到它名下，都立不起来；撤出之前已入在它名下的旧账
     * 照旧列得出来、照旧算在数内。</p>
     *
     * @param row     要判的那一行（改口时为先并好的行）
     * @param newHang 这一挂算不算新挂：头一回入册、或换了挂靠的名录，传 true；
     *                留在原名录名下的旧账改口，传 false
     * @return 判定结果
     */
    public SecreVerdict judgeRow(TSecreCarrier row, boolean newHang) {
        if (row == null) {
            return SecreVerdict.fail("row", "入册那一行空着，这一行立不起来");
        }
        // 应交件数一栏
        Integer needNum = row.getNeedNum();
        if (needNum == null) {
            return SecreVerdict.fail("needNum", "应交件数一栏空着，这一行立不起来");
        }
        if (needNum < 0) {
            return SecreVerdict.fail("needNum", "应交件数一栏写成负数，这一行立不起来");
        }
        // 已收件数一栏
        int gotNum = row.getGotNum() == null ? 0 : row.getGotNum();
        if (gotNum > needNum) {
            return SecreVerdict.fail("gotNum", "已收件数一栏冒过了应交件数，这一行立不起来");
        }
        // 名录代号一栏
        String bookNo = row.getBookNo();
        if (StringUtils.isEmpty(bookNo)) {
            return SecreVerdict.fail("bookNo", "名录代号一栏空着，这一行立不起来");
        }
        TSecreOrgBook book = findBook(bookNo);
        if (book == null) {
            return SecreVerdict.fail("bookNo", "名录代号一栏与册面对不上，这一行立不起来");
        }
        // 名录已撤出：只挡新挂，不抹旧账
        if (newHang && book.getStatus() != null && book.getStatus() == BOOK_STATUS_OFF) {
            return SecreVerdict.fail("bookNo", "名录已撤出，往它名下不能再入新行");
        }
        return SecreVerdict.ok(book, settleLack(needNum, gotNum));
    }

    /**
     * 这一屏该见哪几行：按知悉范围划视野。
     *
     * <p>本级经办岗那一屏，只见挂在本级属地（属地一直写到街道）名下那些行；市保密办
     * 那一屏三层都看得见，可看得见的也只是划给它的那几家。哪一头的范围都没给，就是
     * 不加圈的屏（库里入过的每一行都寻得着）；给了范围而圈不到一家名录，那一屏就空着，
     * 不许从邻家借几行来撑场面。</p>
     *
     * @param roadName 本级属地（一直写到街道）；空表示不按属地圈
     * @param bookNos  划给这一屏的名录代号；null 表示不按名录圈，空集合表示一家都没划给
     * @return 视野条件（已含未删除标记）
     */
    public QueryWrapper<TSecreCarrier> scopeWrapper(String roadName, Collection<String> bookNos) {
        QueryWrapper<TSecreCarrier> wrapper = new QueryWrapper<TSecreCarrier>();
        wrapper.eq("del_flag", 0);
        if (StringUtils.isNotEmpty(roadName)) {
            List<TSecreOrgBook> books = secreOrgBookMapper.selectList(new QueryWrapper<TSecreOrgBook>()
                    .eq("del_flag", 0)
                    .eq("road_name", roadName));
            List<String> nos = books.stream().map(TSecreOrgBook::getBookNo)
                    .filter(Objects::nonNull).collect(Collectors.toList());
            if (nos.isEmpty()) {
                // 本级属地名下一家名录也没有，那一屏就空着
                wrapper.apply("1 = 0");
                return wrapper;
            }
            wrapper.in("book_no", nos);
        }
        if (bookNos != null) {
            List<String> nos = bookNos.stream()
                    .filter(s -> s != null && !s.trim().isEmpty())
                    .collect(Collectors.toList());
            if (nos.isEmpty()) {
                // 一家都没划给这一屏，那一屏就空着
                wrapper.apply("1 = 0");
                return wrapper;
            }
            wrapper.in("book_no", nos);
        }
        return wrapper;
    }

    /**
     * 上级名录下发一份，与本机关底册对一遍。
     *
     * <p>同一个代号两头各一份、对不上的时候，以本机关这份作准：全称以本机关那份为准，
     * 在册情形也以本机关那份为准——上级那份说某处已撤号而本机关这份还在册的，本机关
     * 这份不动它。冲突不许悄悄盖掉：逐条点名到代号写进报告，缘由一并落到本地名录
     * 那一份的备注里。本地没有的代号，照下发新入底册，来路记「上级名录下发」。</p>
     *
     * @param superiorBooks 上级下发的那一份名录
     * @return 合并报告（收了几条、新入几册、对不上几条并逐条点名）
     */
    public SecreSyncReport mergeSuperiorBooks(List<TSecreOrgBook> superiorBooks) {
        SecreSyncReport report = new SecreSyncReport();
        if (superiorBooks == null) {
            return report;
        }
        for (TSecreOrgBook up : superiorBooks) {
            report.incrTotal();
            if (up == null || StringUtils.isEmpty(up.getBookNo())) {
                report.incrSkipped();
                continue;
            }
            TSecreOrgBook local = findBook(up.getBookNo());
            if (local == null) {
                // 本地没有这个代号：照下发新入底册
                TSecreOrgBook fresh = new TSecreOrgBook();
                fresh.setBookNo(up.getBookNo());
                fresh.setBookName(up.getBookName());
                fresh.setBookKind(up.getBookKind());
                fresh.setRoadName(up.getRoadName());
                fresh.setSyncKind(SYNC_FROM_ABOVE);
                fresh.setStatus(up.getStatus() == null ? BOOK_STATUS_ON : up.getStatus());
                fresh.setDelFlag(0);
                fresh.setRemark(up.getRemark());
                secreOrgBookMapper.insert(fresh);
                report.incrInserted();
                continue;
            }
            // 两头对不上的地方，以本机关这份作准，本地一栏也不盖
            List<String> reasons = new ArrayList<String>();
            if (!Objects.equals(local.getBookName(), up.getBookName())) {
                reasons.add("全称对不上：上级下发为「" + up.getBookName()
                        + "」，本机关这份为「" + local.getBookName() + "」，以本机关这份为准");
            }
            Integer upStatus = up.getStatus();
            Integer localStatus = local.getStatus();
            if (upStatus != null && !upStatus.equals(localStatus)) {
                reasons.add("在册情形对不上：上级下发为「" + bookStatusText(upStatus)
                        + "」，本机关这份为「" + bookStatusText(localStatus)
                        + "」，以本机关这份为准，本机关这份不动它");
            }
            if (!reasons.isEmpty()) {
                String reason = String.join("；", reasons);
                // 缘由落到本地这份的备注里，本地这份的名录才算交代过
                String note = "名录下发对不上：" + reason;
                local.setRemark(StringUtils.isEmpty(local.getRemark())
                        ? note : local.getRemark() + "；" + note);
                secreOrgBookMapper.updateById(local);
                report.addConflict(up.getBookNo(), reason);
            }
        }
        return report;
    }

    /** 名录情形说成人话 */
    private String bookStatusText(Integer status) {
        if (status == null) {
            return "未写明";
        }
        return status == BOOK_STATUS_OFF ? "已撤出" : "在册";
    }
}
