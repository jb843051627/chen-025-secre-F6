package com.fc.v2.secre;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreHandArbiter;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.service.impl.TSecreHandRowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * 清退移交行服务单测：整册进门、逐行去向、重批只照面、撤批重递、行次不挪、
 * 三数回库重数、旧档两层。
 */
public class TSecreHandRowServiceImplTest {

    private TSecreHandRowServiceImpl service;
    private TSecreHandRowMapper handMapper;
    private TSecreCarrierMapper carrierMapper;
    private final List<TSecreHandRow> store = new ArrayList<TSecreHandRow>();
    private long idSeq = 1000;

    private static TSecreHandRow incoming(String code, String qty, String quali) {
        TSecreHandRow r = new TSecreHandRow();
        r.setItemCode(code);
        if (qty != null) {
            r.setQty(new BigDecimal(qty));
        }
        if (quali != null) {
            r.setQualiNum(new BigDecimal(quali));
        }
        r.setHandKind(SecreHandArbiter.HAND_KIND_LOCAL);
        return r;
    }

    private static TSecreCarrier face(String code) {
        TSecreCarrier c = new TSecreCarrier();
        c.setCarrierNo(code);
        c.setContent("册面题名·" + code);
        c.setBookNo("ML00");
        c.setDelFlag(0);
        return c;
    }

    private static TSecreHandRow stored(String batch, long id, int rowNo, String code,
                                       Integer status, Integer archive, int year) {
        TSecreHandRow r = new TSecreHandRow();
        r.setId(id);
        r.setBatchNo(batch);
        r.setRowNo(rowNo);
        r.setItemCode(code);
        r.setStatus(status);
        r.setArchiveFlag(archive);
        r.setDelFlag(0);
        r.setCreateTime(new GregorianCalendar(year, Calendar.JUNE, 1).getTime());
        return r;
    }

    @BeforeEach
    public void setUp() {
        service = new TSecreHandRowServiceImpl();
        handMapper = mock(TSecreHandRowMapper.class);
        carrierMapper = mock(TSecreCarrierMapper.class);
        SecreHandArbiter arbiter = new SecreHandArbiter();
        ReflectionTestUtils.setField(arbiter, "secreCarrierMapper", carrierMapper);
        ReflectionTestUtils.setField(service, "secreHandArbiter", arbiter);
        ReflectionTestUtils.setField(service, "secreHandRowMapper", handMapper);
        store.clear();

        // 件码对册面
        when(carrierMapper.selectOne(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            w.getSqlSegment();
            for (Object v : w.getParamNameValuePairs().values()) {
                String code = String.valueOf(v);
                if (code.startsWith("JT-")) {
                    return face(code);
                }
            }
            return null;
        });
        // 落库
        when(handMapper.insert(any(TSecreHandRow.class))).thenAnswer(inv -> {
            TSecreHandRow r = inv.getArgument(0);
            r.setId(++idSeq);
            if (r.getCreateTime() == null) {
                r.setCreateTime(new GregorianCalendar(2026, Calendar.OCTOBER, 6).getTime());
            }
            store.add(r);
            return 1;
        });
        // 回库点行：按 wrapper 里出现的列过滤内存账
        when(handMapper.selectList(any(QueryWrapper.class))).thenAnswer(inv -> {
            QueryWrapper<?> w = inv.getArgument(0);
            return filterStore(w);
        });
        when(handMapper.selectCount(any(QueryWrapper.class))).thenAnswer(inv ->
                Integer.valueOf(filterStore(inv.getArgument(0)).size()));
        // 撤批/沉档：按 sqlSet 改内存账
        when(handMapper.update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper.class)))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<?> uw = inv.getArgument(1);
                    Map<String, Object> cond = columnValues(uw);
                    Object idVal = cond.get("id");
                    for (TSecreHandRow r : store) {
                        if (idVal != null && !String.valueOf(r.getId()).equals(String.valueOf(idVal))) {
                            continue;
                        }
                        String set = uw.getSqlSet();
                        if (set.contains("del_flag")) {
                            r.setDelFlag(1);
                        }
                        if (set.contains("archive_flag")) {
                            r.setArchiveFlag(SecreHandArbiter.ARCHIVE_OLD);
                        }
                    }
                    return 1;
                });
    }

    /** 从 wrapper 的 where 段里按 列→MPGENVAL 键 取出等值条件值（顺序即条件顺序）。 */
    private static final java.util.regex.Pattern EQ_PATTERN = java.util.regex.Pattern.compile(
            "([a-z_]+)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)}");

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> columnValues(AbstractWrapper w) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        String seg = w.getSqlSegment();
        java.util.regex.Matcher m = EQ_PATTERN.matcher(seg);
        while (m.find()) {
            out.put(m.group(1), w.getParamNameValuePairs().get(m.group(2)));
        }
        return out;
    }

    /** 按 wrapper 中的等值列过滤内存账。 */
    private List<TSecreHandRow> filterStore(Wrapper<?> wrapper) {
        Map<String, Object> cond = columnValues((AbstractWrapper) wrapper);
        List<TSecreHandRow> out = new ArrayList<TSecreHandRow>();
        for (TSecreHandRow r : store) {
            boolean keep = true;
            for (Map.Entry<String, Object> e : cond.entrySet()) {
                Object v = e.getValue();
                switch (e.getKey()) {
                    case "batch_no":
                        keep &= String.valueOf(v).equals(r.getBatchNo());
                        break;
                    case "del_flag":
                        keep &= Integer.valueOf(((Number) v).intValue()).equals(r.getDelFlag());
                        break;
                    case "status":
                        keep &= v.equals(r.getStatus());
                        break;
                    case "archive_flag":
                        keep &= v.equals(r.getArchiveFlag());
                        break;
                    default:
                        break;
                }
            }
            if (keep) {
                out.add(r);
            }
        }
        return out;
    }

    // ---------------- 第一/二桩：逐行去向，退回只带本行 ----------------

    @Test
    public void submit_mixedRows_receiptCountedFromStore() {
        List<TSecreHandRow> rows = Arrays.asList(
                incoming("JT-001", "10", "8"),
                incoming("JT-002", "0", "0"),      // 报成零：只退这一行
                incoming(null, "10", "8"),         // 件码空着：只退这一行
                incoming("JT-003", "5", "5"));
        SecreHandReceipt rc = service.submitBatch("B1", rows);
        // 三数回库逐行数：3 原报…不，4 原报，2 落，2 退
        assertEquals(4, rc.getReported());
        assertEquals(2, rc.getLanded());
        assertEquals(2, rc.getReturned());
        assertTrue(rc.balances());
        assertTrue(rc.isReceived());
        assertFalse(rc.isEmpty());
        // 四行都在库里，行次 1..4 排定；两行收下、两行退回，谁也不压谁
        ArgumentCaptor<TSecreHandRow> cap = ArgumentCaptor.forClass(TSecreHandRow.class);
        verify(handMapper, times(4)).insert(cap.capture());
        List<TSecreHandRow> written = cap.getAllValues();
        assertEquals(Integer.valueOf(1), written.get(0).getRowNo());
        assertEquals(Integer.valueOf(2), written.get(1).getRowNo());
        assertEquals(Integer.valueOf(3), written.get(2).getRowNo());
        assertEquals(Integer.valueOf(4), written.get(3).getRowNo());
        assertEquals(SecreHandArbiter.STATUS_RECEIVED, written.get(0).getStatus());
        assertEquals(SecreHandArbiter.STATUS_RETURNED, written.get(1).getStatus());
        assertEquals(SecreHandArbiter.STATUS_RETURNED, written.get(2).getStatus());
        assertEquals(SecreHandArbiter.STATUS_RECEIVED, written.get(3).getStatus());
        // 退回缘故只写在这一行上
        assertTrue(written.get(1).getRemark().contains("应交件数报成零"));
        assertTrue(written.get(1).getRemark().contains("[qty]"));
        assertTrue(written.get(2).getRemark().contains("[itemCode]"));
        assertNull(written.get(0).getRemark());
        assertNull(written.get(3).getRemark());
        // 没落成的行把纸面行次随回执带出去
        assertEquals(Integer.valueOf(2), rc.getReturnedRows().get(0).getRowNo());
        assertEquals(Integer.valueOf(3), rc.getReturnedRows().get(1).getRowNo());
    }

    @Test
    public void submit_sameCodeTwice_twoIndependentRows() {
        // 岭南区那册的毛病不许重现：同一件码两行是两本账，后落的不压先前的
        SecreHandReceipt rc = service.submitBatch("B2", Arrays.asList(
                incoming("JT-001", "10", "10"),
                incoming("JT-001", "3", "0")));
        assertEquals(2, rc.getLanded());
        assertEquals(0, rc.getReturned());
        verify(handMapper, times(2)).insert(any(TSecreHandRow.class));
    }

    @Test
    public void submit_acceptedRow_linkedByFaceCode() {
        // 件码念得不全（带空格的假码）：认载体只认件码，对不上册面就退回
        TSecreHandRow r = incoming(" jt-5 ", "1", "1");
        SecreHandReceipt rc = service.submitBatch("B2b", Arrays.asList(r));
        assertEquals(0, rc.getLanded());
        assertEquals(1, rc.getReturned());
    }

    // ---------------- 第二桩：越限整册退走，库里不留半行 ----------------

    @Test
    public void submit_oversize_wholeReturned_nothingStored() {
        List<TSecreHandRow> rows = new ArrayList<TSecreHandRow>();
        for (int i = 0; i < SecreHandArbiter.MAX_ROWS_PER_BOOK + 1; i++) {
            rows.add(incoming("JT-001", "1", "1"));
        }
        SecreHandReceipt rc = service.submitBatch("B3", rows);
        assertTrue(rc.isWholeReturned());
        assertTrue(rc.getWholeReason().contains("整册原样退走"));
        verify(handMapper, never()).insert(any(TSecreHandRow.class));
    }

    // ---------------- 第三桩：重批只照面，撤净才许重递 ----------------

    @Test
    public void submit_secondTime_sameBatch_onlyReconciles() {
        store.add(stored("B4", 1L, 1, "JT-001", SecreHandArbiter.STATUS_RECEIVED,
                SecreHandArbiter.ARCHIVE_CURRENT, 2026));
        SecreHandReceipt rc = service.submitBatch("B4", Arrays.asList(
                incoming("JT-001", "99", "0"),   // 哪怕内容改了，也一行不添不减
                incoming("JT-002", "1", "1")));
        // 回执复述的是头一遍库里那一本账
        assertEquals(1, rc.getReported());
        assertEquals(1, rc.getLanded());
        verify(handMapper, never()).insert(any(TSecreHandRow.class));
    }

    @Test
    public void submit_secondTime_otherHandKind_stillOnlyReconciles() {
        // 本级送交与上级调回两条来路，同一个门里进第二次，不另开一册
        store.add(stored("B8", 1L, 1, "JT-001", SecreHandArbiter.STATUS_RECEIVED,
                SecreHandArbiter.ARCHIVE_CURRENT, 2026));
        TSecreHandRow fromAbove = incoming("JT-007", "1", "1");
        fromAbove.setHandKind(SecreHandArbiter.HAND_KIND_ABOVE);
        SecreHandReceipt rc = service.submitBatch("B8", Arrays.asList(fromAbove));
        assertEquals(1, rc.getReported());
        assertEquals(1, rc.getLanded());
        verify(handMapper, never()).insert(any(TSecreHandRow.class));
    }

    @Test
    public void revoke_thenResubmit_storesAgain() {
        store.add(stored("B", 1L, 1, "JT-001", SecreHandArbiter.STATUS_RECEIVED,
                SecreHandArbiter.ARCHIVE_CURRENT, 2026));
        assertEquals(1, service.revokeBatch("B"));
        // 撤干净：del_flag=1，现行账点不到了
        assertEquals(1, store.get(0).getDelFlag());
        // 同批次码重递才谈得上
        SecreHandReceipt rc = service.submitBatch("B", Arrays.asList(incoming("JT-002", "2", "1")));
        assertEquals(1, rc.getReported());
        assertEquals(1, rc.getLanded());
        verify(handMapper, times(1)).insert(any(TSecreHandRow.class));
    }

    @Test
    public void rowNo_stableAcrossReceiptCalls() {
        // 头一日退回去的行，隔一日再查仍是原先那一个行次
        service.submitBatch("B5", Arrays.asList(
                incoming("JT-001", "1", "1"),
                incoming("BAD", "1", "1"),
                incoming("JT-003", "1", "1")));
        SecreHandReceipt day1 = service.receipt("B5");
        SecreHandReceipt day2 = service.receipt("B5");
        assertEquals(day1.getReturnedRows().get(0).getRowNo(),
                day2.getReturnedRows().get(0).getRowNo());
        assertEquals(Integer.valueOf(2), day2.getReturnedRows().get(0).getRowNo());
        // 两处是同一回算的，才谈得上一致
        assertEquals(day1.getReported(), day2.getReported());
        assertEquals(day1.getLanded(), day2.getLanded());
        assertEquals(day1.getReturned(), day2.getReturned());
    }

    // ---------------- 第四桩：空批不出收讫 ----------------

    @Test
    public void submit_emptyBook_emptyReceipt() {
        SecreHandReceipt rc = service.submitBatch("B6", new ArrayList<TSecreHandRow>());
        assertEquals(0, rc.getReported());
        assertTrue(rc.isEmpty());
        assertFalse(rc.isReceived());
        verify(handMapper, never()).insert(any(TSecreHandRow.class));
    }

    @Test
    public void submit_allReturned_emptyBatchNoReceived() {
        SecreHandReceipt rc = service.submitBatch("B7", Arrays.asList(
                incoming(null, "1", "1"),
                incoming("NOPE", "1", "1")));
        assertEquals(2, rc.getReported());
        assertEquals(0, rc.getLanded());
        assertEquals(2, rc.getReturned());
        assertTrue(rc.isEmpty());
        assertFalse(rc.isReceived());
    }

    // ---------------- 第五桩：旧档两层 ----------------

    @Test
    public void currentAndArchiveLayers_listSeparately() {
        store.add(stored("B", 1L, 1, "JT-001", SecreHandArbiter.STATUS_RECEIVED,
                SecreHandArbiter.ARCHIVE_CURRENT, 2026));
        store.add(stored("B", 2L, 2, "JT-002", SecreHandArbiter.STATUS_PENDING,
                SecreHandArbiter.ARCHIVE_OLD, 2023));
        assertEquals(1, service.listCurrentRows("B").size());
        assertEquals(1, service.listArchiveRows("B").size());
        // 先前收过的（与全部行）照旧翻得着、一条不少
        assertEquals(2, service.listBatchRows("B").size());
    }

    @Test
    public void sink_onlyPendingTwoYearsSinks() {
        store.add(stored("B", 1L, 1, "JT-OLD", SecreHandArbiter.STATUS_PENDING,
                SecreHandArbiter.ARCHIVE_CURRENT, 2024));
        store.add(stored("B", 2L, 2, "JT-NEW", SecreHandArbiter.STATUS_PENDING,
                SecreHandArbiter.ARCHIVE_CURRENT, 2025));
        store.add(stored("B", 3L, 3, "JT-GOT", SecreHandArbiter.STATUS_RECEIVED,
                SecreHandArbiter.ARCHIVE_CURRENT, 2023));
        int sunk = service.sinkExpiredRows(2026);
        assertEquals(1, sunk);
        assertEquals(SecreHandArbiter.ARCHIVE_OLD, store.get(0).getArchiveFlag());
        assertEquals(SecreHandArbiter.ARCHIVE_CURRENT, store.get(1).getArchiveFlag());
        // 先前收过的不沉，照旧翻得着
        assertEquals(SecreHandArbiter.ARCHIVE_CURRENT, store.get(2).getArchiveFlag());
    }
}
