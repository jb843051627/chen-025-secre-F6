package com.fc.v2.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreHandArbiter;
import com.fc.v2.common.secre.SecreHandMismatch;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.common.secre.SecreHandVerdict;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 清退移交判定核（这一处）单测：两数并到、件码认全、单行退回、行次三数、
 * 册面对照、旧档沉取。
 */
public class SecreHandArbiterTest {

    private SecreHandArbiter arbiter;
    private TSecreCarrierMapper carrierMapper;
    private Map<String, TSecreCarrier> faces;

    private static TSecreCarrier face(String code, String content, String bookNo) {
        TSecreCarrier c = new TSecreCarrier();
        c.setCarrierNo(code);
        c.setContent(content);
        c.setBookNo(bookNo);
        c.setDelFlag(0);
        return c;
    }

    private static TSecreHandRow row(Integer rowNo, String code, String qty, String quali) {
        TSecreHandRow r = new TSecreHandRow();
        r.setRowNo(rowNo);
        r.setItemCode(code);
        r.setQty(qty == null ? null : new BigDecimal(qty));
        r.setQualiNum(quali == null ? null : new BigDecimal(quali));
        r.setHandKind(SecreHandArbiter.HAND_KIND_LOCAL);
        return r;
    }

    @BeforeEach
    public void setUp() {
        arbiter = new SecreHandArbiter();
        carrierMapper = mock(TSecreCarrierMapper.class);
        ReflectionTestUtils.setField(arbiter, "secreCarrierMapper", carrierMapper);
        faces = new HashMap<String, TSecreCarrier>();
        faces.put("JT-001", face("JT-001", "某年测绘图一套", "ML00"));
        faces.put("JT-002", face("JT-002", "红头文件清退件", "ML02"));
        when(carrierMapper.selectOne(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                TSecreCarrier f = faces.get(String.valueOf(v));
                if (f != null) {
                    return f;
                }
            }
            return null;
        });
    }

    // ---------------- 第一桩：一行挂两个数，认载体只认件码 ----------------

    @Test
    public void judge_ok() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "10", "8"));
        assertTrue(v.isOk());
        assertEquals("JT-001", v.getFace().getCarrierNo());
    }

    @Test
    public void judge_qtyMissing_rejected_noSubstitute() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", null, "8"));
        assertFalse(v.isOk());
        assertEquals("qty", v.getField());
        assertTrue(v.getMsg().contains("应交件数空着"));
    }

    @Test
    public void judge_qualiMissing_rejected_noSubstitute() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "10", null));
        assertFalse(v.isOk());
        assertEquals("qualiNum", v.getField());
        assertTrue(v.getMsg().contains("核收合格件数空着"));
    }

    @Test
    public void judge_qtyZero_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "0", "0"));
        assertFalse(v.isOk());
        assertEquals("qty", v.getField());
        assertTrue(v.getMsg().contains("报成零"));
    }

    @Test
    public void judge_qtyNegative_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "-2", "0"));
        assertFalse(v.isOk());
        assertEquals("qty", v.getField());
        assertTrue(v.getMsg().contains("负数"));
    }

    @Test
    public void judge_qtyOverCap_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "10000", "1"));
        assertFalse(v.isOk());
        assertEquals("qty", v.getField());
        assertTrue(v.getMsg().contains("核收上限"));
    }

    @Test
    public void judge_qtyAtCap_accepted() {
        assertTrue(arbiter.judgeRow(row(1, "JT-001", "9999.99", "9999.99")).isOk());
    }

    @Test
    public void judge_qualiNegative_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "10", "-1"));
        assertFalse(v.isOk());
        assertEquals("qualiNum", v.getField());
    }

    @Test
    public void judge_qualiOverQty_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-001", "5", "6"));
        assertFalse(v.isOk());
        assertEquals("qualiNum", v.getField());
        assertTrue(v.getMsg().contains("冒过"));
    }

    @Test
    public void judge_codeBlank_rejected() {
        SecreHandVerdict v = arbiter.judgeRow(row(1, "  ", "10", "8"));
        assertFalse(v.isOk());
        assertEquals("itemCode", v.getField());
        assertTrue(v.getMsg().contains("件码空着"));
    }

    @Test
    public void judge_codeTooLong_rejected() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            sb.append('J');
        }
        SecreHandVerdict v = arbiter.judgeRow(row(1, sb.toString(), "10", "8"));
        assertFalse(v.isOk());
        assertEquals("itemCode", v.getField());
        assertTrue(v.getMsg().contains("一把尺"));
    }

    @Test
    public void judge_codeUnknown_rejected_titleCannotPatch() {
        // 件码对不上册面，拿题名去凑也不算对上
        SecreHandVerdict v = arbiter.judgeRow(row(1, "JT-404", "10", "8"));
        assertFalse(v.isOk());
        assertEquals("itemCode", v.getField());
        assertTrue(v.getMsg().contains("册面"));
    }

    @Test
    public void judge_sameCodeTwice_twoIndependentBooks() {
        // 同一件码占两行：两本账，谁的去向都压不到对方身上
        SecreHandVerdict a = arbiter.judgeRow(row(1, "JT-001", "10", "10"));
        SecreHandVerdict b = arbiter.judgeRow(row(2, "JT-001", "3", "0"));
        assertTrue(a.isOk());
        assertTrue(b.isOk());
    }

    // ---------------- 第五桩：题名、名录代号以册面为凭 ----------------

    @Test
    public void compareFace_contentMismatch_namedButAccepted() {
        TSecreHandRow r = row(3, "JT-001", "10", "8");
        r.setReportedContent("报文里另写的题名");
        SecreHandVerdict v = arbiter.judgeRow(r);
        assertTrue(v.isOk()); // 对不上不挡收
        assertEquals(1, v.getMismatches().size());
        SecreHandMismatch m = v.getMismatches().get(0);
        assertEquals("content", m.getField());
        assertEquals(Integer.valueOf(3), m.getRowNo());
        assertEquals("JT-001", m.getItemCode());
        assertTrue(m.getMsg().contains("以册面为准"));
    }

    @Test
    public void compareFace_bookNoMismatch_named() {
        TSecreHandRow r = row(4, "JT-002", "10", "8");
        r.setReportedBookNo("ML99");
        SecreHandVerdict v = arbiter.judgeRow(r);
        assertTrue(v.isOk());
        assertEquals("bookNo", v.getMismatches().get(0).getField());
    }

    @Test
    public void compareFace_matchOrBlank_noMismatch() {
        TSecreHandRow r = row(5, "JT-001", "10", "8");
        r.setReportedContent("某年测绘图一套");
        r.setReportedBookNo("ML00");
        assertTrue(arbiter.judgeRow(r).getMismatches().isEmpty());
        // 报文两栏空着：不作对照、不点名
        assertTrue(arbiter.judgeRow(row(6, "JT-001", "10", "8")).getMismatches().isEmpty());
    }

    // ---------------- 第二桩：单册行数上限 ----------------

    @Test
    public void oversizeBook() {
        assertFalse(arbiter.oversizeBook(500));
        assertTrue(arbiter.oversizeBook(501));
    }

    // ---------------- 第四桩：三数回库逐行数 ----------------

    private TSecreHandRow stored(int rowNo, String code, Integer status, String remark) {
        TSecreHandRow r = new TSecreHandRow();
        r.setBatchNo("PC-1");
        r.setRowNo(rowNo);
        r.setItemCode(code);
        r.setStatus(status);
        r.setRemark(remark);
        r.setDelFlag(0);
        return r;
    }

    @Test
    public void receipt_countsFromRows_balances() {
        String reason = arbiter.writeReturnRemark("qty", "应交件数报成零，原路退回这一行");
        String mismatch = arbiter.writeMismatchRemark(
                new SecreHandMismatch(1, "JT-001", "content", "题名对不上，以册面为准"));
        List<TSecreHandRow> rows = Arrays.asList(
                stored(1, "JT-001", SecreHandArbiter.STATUS_RECEIVED, mismatch),
                stored(2, null, SecreHandArbiter.STATUS_RETURNED, reason),
                stored(3, "JT-002", SecreHandArbiter.STATUS_RECEIVED, null));
        SecreHandReceipt rc = arbiter.buildReceipt("PC-1", rows);
        assertEquals(3, rc.getReported());
        assertEquals(2, rc.getLanded());
        assertEquals(1, rc.getReturned());
        assertTrue(rc.balances());
        assertTrue(rc.isReceived());
        assertFalse(rc.isEmpty());
        // 没落成的那一行仍把纸面行次带出去
        assertEquals(Integer.valueOf(2), rc.getReturnedRows().get(0).getRowNo());
        assertEquals("qty", rc.getReturnedRows().get(0).getField());
        // 对不上的行点名报出
        assertEquals(1, rc.getMismatches().size());
        assertEquals(Integer.valueOf(1), rc.getMismatches().get(0).getRowNo());
        assertEquals("content", rc.getMismatches().get(0).getField());
    }

    @Test
    public void receipt_emptyBook_emptyNoReceived() {
        SecreHandReceipt rc = arbiter.buildReceipt("PC-X", new ArrayList<TSecreHandRow>());
        assertEquals(0, rc.getReported());
        assertTrue(rc.isEmpty());
        assertFalse(rc.isReceived());
        assertTrue(rc.balances());
    }

    @Test
    public void receipt_allReturned_emptyNoReceived() {
        List<TSecreHandRow> rows = Arrays.asList(
                stored(1, null, SecreHandArbiter.STATUS_RETURNED,
                        arbiter.writeReturnRemark("qty", "报成零")),
                stored(2, "X", SecreHandArbiter.STATUS_RETURNED,
                        arbiter.writeReturnRemark("itemCode", "对不上")));
        SecreHandReceipt rc = arbiter.buildReceipt("PC-2", rows);
        assertEquals(2, rc.getReported());
        assertEquals(0, rc.getLanded());
        assertEquals(2, rc.getReturned());
        assertTrue(rc.isEmpty());       // 一行都没收着：空批
        assertFalse(rc.isReceived());   // 不出收讫
        // 行次仍在，与机关纸面行行对得上
        assertEquals(Integer.valueOf(1), rc.getReturnedRows().get(0).getRowNo());
        assertEquals(Integer.valueOf(2), rc.getReturnedRows().get(1).getRowNo());
    }

    @Test
    public void receipt_orderedByRowNoEvenWhenStoredOutOfOrder() {
        // 库里哪怕乱序回出，回执仍按行次序点，排定不再挪
        List<TSecreHandRow> rows = Arrays.asList(
                stored(3, "C", SecreHandArbiter.STATUS_RETURNED,
                        arbiter.writeReturnRemark("qty", "零")),
                stored(1, "A", SecreHandArbiter.STATUS_RECEIVED, null),
                stored(2, "B", SecreHandArbiter.STATUS_RECEIVED, null));
        SecreHandReceipt rc = arbiter.buildReceipt("PC-3", rows);
        assertEquals(Integer.valueOf(3), rc.getReturnedRows().get(0).getRowNo());
    }

    // ---------------- 第五桩：旧档沉取 ----------------

    private TSecreHandRow pendingAt(int year) {
        TSecreHandRow r = new TSecreHandRow();
        r.setStatus(SecreHandArbiter.STATUS_PENDING);
        r.setArchiveFlag(SecreHandArbiter.ARCHIVE_CURRENT);
        Calendar c = new GregorianCalendar(year, Calendar.MARCH, 1);
        r.setCreateTime(c.getTime());
        return r;
    }

    @Test
    public void sink_pendingTwoYears_sinks() {
        assertTrue(arbiter.shouldSink(pendingAt(2024), 2026));
    }

    @Test
    public void sink_pendingOneYear_stays() {
        assertFalse(arbiter.shouldSink(pendingAt(2025), 2026));
    }

    @Test
    public void sink_receivedOldRow_neverSinks() {
        TSecreHandRow r = pendingAt(2023);
        r.setStatus(SecreHandArbiter.STATUS_RECEIVED); // 先前收过的照旧翻得着
        assertFalse(arbiter.shouldSink(r, 2026));
    }

    @Test
    public void sink_alreadyArchived_notAgain() {
        TSecreHandRow r = pendingAt(2020);
        r.setArchiveFlag(SecreHandArbiter.ARCHIVE_OLD);
        assertFalse(arbiter.shouldSink(r, 2026));
    }
}
