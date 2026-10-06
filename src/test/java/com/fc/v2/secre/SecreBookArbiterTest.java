package com.fc.v2.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.common.secre.SecreSyncReport;
import com.fc.v2.common.secre.SecreVerdict;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 判定核（这一处）单测：轧差、立行、视野、名录打架。
 */
public class SecreBookArbiterTest {

    private SecreBookArbiter arbiter;
    private TSecreOrgBookMapper orgBookMapper;
    private Map<String, TSecreOrgBook> books;

    private static TSecreOrgBook book(Long id, String no, String name, Integer status) {
        TSecreOrgBook b = new TSecreOrgBook();
        b.setId(id);
        b.setBookNo(no);
        b.setBookName(name);
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    private static TSecreCarrier row(String bookNo, Integer need, Integer got) {
        TSecreCarrier r = new TSecreCarrier();
        r.setBookNo(bookNo);
        r.setNeedNum(need);
        r.setGotNum(got);
        return r;
    }

    @BeforeEach
    public void setUp() {
        arbiter = new SecreBookArbiter();
        orgBookMapper = mock(TSecreOrgBookMapper.class);
        ReflectionTestUtils.setField(arbiter, "secreOrgBookMapper", orgBookMapper);
        books = new HashMap<String, TSecreOrgBook>();
        books.put("ML00", book(0L, "ML00", "浔州区谷城街道·中共谷城市委机关", 0));
        books.put("ML01", book(1L, "ML01", "市测绘院下属资料室", 1));
        // 对册一律走名录代号：按查询条件里带的代号回册
        when(orgBookMapper.selectOne(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment(); // 参数对懒加载，先落位再读
            for (Object v : w.getParamNameValuePairs().values()) {
                TSecreOrgBook b = books.get(String.valueOf(v));
                if (b != null) {
                    return b;
                }
            }
            return null;
        });
    }

    // ---------------- 轧差：应交减已收 ----------------

    @Test
    public void settleLack_examples() {
        assertEquals(Integer.valueOf(18), arbiter.settleLack(30, 12));
        assertEquals(Integer.valueOf(13), arbiter.settleLack(20, 7));
        assertEquals(Integer.valueOf(0), arbiter.settleLack(20, 20));
        assertEquals(Integer.valueOf(30), arbiter.settleLack(30, null));
    }

    // ---------------- 这一行立不立得住 ----------------

    @Test
    public void judge_ok() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML00", 30, 12));
        assertTrue(v.isOk());
        assertEquals(Integer.valueOf(18), v.getLackNum());
        assertEquals("ML00", v.getBook().getBookNo());
    }

    @Test
    public void judge_needNull_rejected() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML00", null, 1));
        assertFalse(v.isOk());
        assertEquals("needNum", v.getField());
        assertTrue(v.getMsg().contains("应交件数"));
    }

    @Test
    public void judge_needNegative_rejected() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML00", -1, 0));
        assertFalse(v.isOk());
        assertEquals("needNum", v.getField());
        assertTrue(v.getMsg().contains("应交件数"));
    }

    @Test
    public void judge_gotOverNeed_rejected() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML00", 5, 7));
        assertFalse(v.isOk());
        assertEquals("gotNum", v.getField());
        assertTrue(v.getMsg().contains("已收件数"));
    }

    @Test
    public void judge_bookNoBlank_rejected() {
        SecreVerdict v = arbiter.judgeNewRow(row(null, 5, 1));
        assertFalse(v.isOk());
        assertEquals("bookNo", v.getField());
        assertTrue(v.getMsg().contains("名录代号"));
    }

    @Test
    public void judge_bookNoUnknown_rejected() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML99", 5, 1));
        assertFalse(v.isOk());
        assertEquals("bookNo", v.getField());
        assertTrue(v.getMsg().contains("对不上"));
    }

    @Test
    public void judge_withdrawnBook_blocksNewHang() {
        SecreVerdict v = arbiter.judgeNewRow(row("ML01", 5, 1));
        assertFalse(v.isOk());
        assertEquals("bookNo", v.getField());
        assertTrue(v.getMsg().contains("已撤出"));
    }

    @Test
    public void judge_withdrawnBook_keepsOldAccounts() {
        // 撤出之前入在它名下的旧账：照旧立得住、照旧算在数内
        SecreVerdict v = arbiter.judgeRow(row("ML01", 5, 1), false);
        assertTrue(v.isOk());
        assertEquals(Integer.valueOf(4), v.getLackNum());
    }

    // ---------------- 这一屏该见哪几行 ----------------

    @Test
    public void scope_byRoad_filtersByBookNo() {
        when(orgBookMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(Collections.singletonList(books.get("ML00")));
        QueryWrapper<TSecreCarrier> w = arbiter.scopeWrapper("浔州省—谷城市—浔州区—谷城街道", null);
        String seg = w.getSqlSegment();
        assertTrue(seg.contains("del_flag"));
        assertTrue(seg.contains("book_no"));
        assertTrue(seg.contains("IN"));
    }

    @Test
    public void scope_byRoad_noBookUnderRoad_emptyScreen() {
        when(orgBookMapper.selectList(any(QueryWrapper.class))).thenReturn(Collections.emptyList());
        QueryWrapper<TSecreCarrier> w = arbiter.scopeWrapper("浔州省—谷城市—北塔区—岭南街道", null);
        assertTrue(w.getSqlSegment().contains("1 = 0"));
    }

    @Test
    public void scope_byAssignedBookNos() {
        QueryWrapper<TSecreCarrier> w = arbiter.scopeWrapper(null, Arrays.asList("ML00", "ML02"));
        String seg = w.getSqlSegment();
        assertTrue(seg.contains("book_no"));
        assertFalse(seg.contains("1 = 0"));
    }

    @Test
    public void scope_emptyAssignedBookNos_emptyScreen() {
        QueryWrapper<TSecreCarrier> w = arbiter.scopeWrapper(null, Collections.<String>emptyList());
        assertTrue(w.getSqlSegment().contains("1 = 0"));
    }

    @Test
    public void scope_unrestricted_findsEveryRow() {
        QueryWrapper<TSecreCarrier> w = arbiter.scopeWrapper(null, null);
        String seg = w.getSqlSegment();
        assertTrue(seg.contains("del_flag"));
        assertFalse(seg.contains("1 = 0"));
        assertFalse(seg.contains("book_no"));
    }

    // ---------------- 两份名录打架 ----------------

    @Test
    public void merge_newBookNo_insertedAsFromAbove() {
        TSecreOrgBook up = book(null, "ML07", "新入册单位", 0);
        SecreSyncReport report = arbiter.mergeSuperiorBooks(Collections.singletonList(up));
        assertEquals(1, report.getTotal());
        assertEquals(1, report.getInserted());
        assertTrue(report.getConflicts().isEmpty());
        ArgumentCaptor<TSecreOrgBook> cap = ArgumentCaptor.forClass(TSecreOrgBook.class);
        verify(orgBookMapper).insert(cap.capture());
        assertEquals("ML07", cap.getValue().getBookNo());
        assertEquals(SecreBookArbiter.SYNC_FROM_ABOVE, cap.getValue().getSyncKind());
    }

    @Test
    public void merge_nameConflict_localWinsAndRemarked() {
        TSecreOrgBook up = book(null, "ML00", "上级那份的另一个全称", 0);
        SecreSyncReport report = arbiter.mergeSuperiorBooks(Collections.singletonList(up));
        // 逐条点名，点到代号
        assertEquals(1, report.getConflicts().size());
        assertEquals("ML00", report.getConflicts().get(0).getBookNo());
        ArgumentCaptor<TSecreOrgBook> cap = ArgumentCaptor.forClass(TSecreOrgBook.class);
        verify(orgBookMapper).updateById(cap.capture());
        TSecreOrgBook written = cap.getValue();
        // 全称以本机关这份为准，不许悄悄盖掉
        assertEquals("浔州区谷城街道·中共谷城市委机关", written.getBookName());
        // 缘由落到本地这份的备注里
        assertTrue(written.getRemark().contains("全称对不上"));
        assertTrue(written.getRemark().contains("以本机关这份为准"));
    }

    @Test
    public void merge_superiorSaysWithdrawn_localStaysOnBook() {
        TSecreOrgBook up = book(null, "ML00", "浔州区谷城街道·中共谷城市委机关", 1);
        SecreSyncReport report = arbiter.mergeSuperiorBooks(Collections.singletonList(up));
        assertEquals(1, report.getConflicts().size());
        assertEquals("ML00", report.getConflicts().get(0).getBookNo());
        ArgumentCaptor<TSecreOrgBook> cap = ArgumentCaptor.forClass(TSecreOrgBook.class);
        verify(orgBookMapper).updateById(cap.capture());
        TSecreOrgBook written = cap.getValue();
        // 上级那份说已撤号，本机关这份还在册的，本机关这份不动它
        assertEquals(Integer.valueOf(0), written.getStatus());
        assertTrue(written.getRemark().contains("在册情形对不上"));
    }

    @Test
    public void merge_noConflict_nothingTouched() {
        TSecreOrgBook up = book(null, "ML00", "浔州区谷城街道·中共谷城市委机关", 0);
        SecreSyncReport report = arbiter.mergeSuperiorBooks(Collections.singletonList(up));
        assertTrue(report.getConflicts().isEmpty());
        verify(orgBookMapper, never()).updateById(any());
        verify(orgBookMapper, never()).insert(any(TSecreOrgBook.class));
    }

    @Test
    public void merge_blankBookNo_skipped() {
        SecreSyncReport report = arbiter.mergeSuperiorBooks(
                Collections.singletonList(book(null, null, "没写代号", 0)));
        assertEquals(1, report.getSkipped());
        assertTrue(report.getConflicts().isEmpty());
    }
}
