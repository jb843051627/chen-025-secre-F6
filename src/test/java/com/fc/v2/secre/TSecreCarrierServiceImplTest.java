package com.fc.v2.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.common.secre.SecreVerdict;
import com.fc.v2.mapper.auto.TSecreCarrierMapper;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.impl.TSecreCarrierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 册面服务单测：落库重轧、改口重轧、走代号挂接、撤出挡新行不抹旧账。
 */
public class TSecreCarrierServiceImplTest {

    private TSecreCarrierServiceImpl service;
    private TSecreCarrierMapper carrierMapper;
    private TSecreOrgBookMapper orgBookMapper;
    private Map<String, TSecreOrgBook> books;

    private static TSecreOrgBook book(Long id, String no, Integer status) {
        TSecreOrgBook b = new TSecreOrgBook();
        b.setId(id);
        b.setBookNo(no);
        b.setBookName(no + "单位");
        b.setStatus(status);
        b.setDelFlag(0);
        return b;
    }

    private static TSecreCarrier row(Long id, String bookNo, Integer need, Integer got) {
        TSecreCarrier r = new TSecreCarrier();
        r.setId(id);
        r.setBookNo(bookNo);
        r.setNeedNum(need);
        r.setGotNum(got);
        return r;
    }

    @BeforeEach
    public void setUp() {
        service = new TSecreCarrierServiceImpl();
        carrierMapper = mock(TSecreCarrierMapper.class);
        orgBookMapper = mock(TSecreOrgBookMapper.class);
        SecreBookArbiter arbiter = new SecreBookArbiter();
        ReflectionTestUtils.setField(arbiter, "secreOrgBookMapper", orgBookMapper);
        ReflectionTestUtils.setField(service, "baseMapper", carrierMapper);
        ReflectionTestUtils.setField(service, "secreBookArbiter", arbiter);
        books = new HashMap<String, TSecreOrgBook>();
        books.put("ML00", book(0L, "ML00", 0));
        books.put("ML01", book(1L, "ML01", 1));
        books.put("ML02", book(2L, "ML02", 0));
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
        when(carrierMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(carrierMapper.insert(any(TSecreCarrier.class))).thenReturn(1);
        when(carrierMapper.update(any(TSecreCarrier.class), any())).thenReturn(1);
    }

    // ---------------- 头一回入册要轧 ----------------

    @Test
    public void insert_settlesLack_reportedValueIgnored() {
        TSecreCarrier record = row(null, "ML00", 30, 12);
        record.setLackNum(999); // 报文里带了多少都不作数
        int r = service.insertTSecreCarrier(record);
        assertEquals(1, r);
        ArgumentCaptor<TSecreCarrier> cap = ArgumentCaptor.forClass(TSecreCarrier.class);
        verify(carrierMapper).insert(cap.capture());
        TSecreCarrier written = cap.getValue();
        assertEquals(Integer.valueOf(18), written.getLackNum());
        // 走名录代号挂接：以代号对出的名录回填
        assertEquals("ML00", written.getBookNo());
        assertEquals(Integer.valueOf(0), written.getBookId());
    }

    @Test
    public void insert_evenBalance_lackZero() {
        TSecreCarrier record = row(null, "ML00", 20, 20);
        assertEquals(1, service.insertTSecreCarrier(record));
        ArgumentCaptor<TSecreCarrier> cap = ArgumentCaptor.forClass(TSecreCarrier.class);
        verify(carrierMapper).insert(cap.capture());
        assertEquals(Integer.valueOf(0), cap.getValue().getLackNum());
    }

    @Test
    public void insert_needNull_rejected() {
        assertEquals(0, service.insertTSecreCarrier(row(null, "ML00", null, 1)));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void insert_needNegative_rejected() {
        assertEquals(0, service.insertTSecreCarrier(row(null, "ML00", -3, 0)));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void insert_gotOverNeed_rejected() {
        assertEquals(0, service.insertTSecreCarrier(row(null, "ML00", 5, 7)));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void insert_unknownBookNo_rejected() {
        assertEquals(0, service.insertTSecreCarrier(row(null, "ML99", 5, 1)));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void insert_withdrawnBook_rejected() {
        assertEquals(0, service.insertTSecreCarrier(row(null, "ML01", 5, 1)));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void insert_dupCarrierNo_rejected() {
        when(carrierMapper.selectCount(any(QueryWrapper.class))).thenReturn(1);
        TSecreCarrier record = row(null, "ML00", 5, 1);
        record.setCarrierNo("C-001");
        assertEquals(0, service.insertTSecreCarrier(record));
        verify(carrierMapper, never()).insert(any(TSecreCarrier.class));
    }

    @Test
    public void register_returnsVerdict_withSameLackAsWritten() {
        TSecreCarrier record = row(null, "ML00", 20, 7);
        record.setLackNum(12345);
        SecreVerdict v = service.registerTSecreCarrier(record);
        assertTrue(v.isOk());
        assertEquals(Integer.valueOf(13), v.getLackNum());
        // 页面上摆出来的数与回库逐行点出来的那一遍，同一处算的账
        assertEquals(Integer.valueOf(13), v.getRow().getLackNum());
    }

    @Test
    public void register_rejection_namesField() {
        SecreVerdict v = service.registerTSecreCarrier(row(null, "ML00", 5, 9));
        assertFalse(v.isOk());
        assertEquals("gotNum", v.getField());
        assertTrue(v.getMsg().contains("已收件数"));
    }

    // ---------------- 改口要轧 ----------------

    private void stubDbRow(TSecreCarrier db) {
        when(carrierMapper.selectOne(any(QueryWrapper.class))).thenReturn(db);
    }

    @Test
    public void update_gotNumChanged_recomputesLack() {
        stubDbRow(row(7L, "ML00", 30, 12));
        TSecreCarrier record = new TSecreCarrier();
        record.setId(7L);
        record.setGotNum(20);
        record.setLackNum(888); // 报文里带的不作数
        assertEquals(1, service.updateTSecreCarrier(record));
        ArgumentCaptor<TSecreCarrier> cap = ArgumentCaptor.forClass(TSecreCarrier.class);
        verify(carrierMapper).update(cap.capture(), any());
        assertEquals(Integer.valueOf(10), cap.getValue().getLackNum());
        assertEquals(Integer.valueOf(20), cap.getValue().getGotNum());
    }

    @Test
    public void update_bookChanged_relinksByBookNoAndRecomputes() {
        stubDbRow(row(7L, "ML00", 30, 12));
        TSecreCarrier record = new TSecreCarrier();
        record.setId(7L);
        record.setBookNo("ML02");
        assertEquals(1, service.updateTSecreCarrier(record));
        ArgumentCaptor<TSecreCarrier> cap = ArgumentCaptor.forClass(TSecreCarrier.class);
        verify(carrierMapper).update(cap.capture(), any());
        TSecreCarrier written = cap.getValue();
        assertEquals("ML02", written.getBookNo());
        assertEquals(Integer.valueOf(2), written.getBookId());
        assertEquals(Integer.valueOf(18), written.getLackNum());
    }

    @Test
    public void update_rehangToWithdrawnBook_rejected() {
        stubDbRow(row(7L, "ML00", 30, 12));
        TSecreCarrier record = new TSecreCarrier();
        record.setId(7L);
        record.setBookNo("ML01");
        assertEquals(0, service.updateTSecreCarrier(record));
        verify(carrierMapper, never()).update(any(TSecreCarrier.class), any());
    }

    @Test
    public void update_oldRowUnderWithdrawnBook_stillCountable() {
        // 名录撤出之前入在它名下的旧账：照旧改口、照旧轧差
        stubDbRow(row(7L, "ML01", 10, 2));
        TSecreCarrier record = new TSecreCarrier();
        record.setId(7L);
        record.setGotNum(5);
        assertEquals(1, service.updateTSecreCarrier(record));
        ArgumentCaptor<TSecreCarrier> cap = ArgumentCaptor.forClass(TSecreCarrier.class);
        verify(carrierMapper).update(cap.capture(), any());
        assertEquals(Integer.valueOf(5), cap.getValue().getLackNum());
    }

    @Test
    public void update_mergedRowInvalid_rejected() {
        stubDbRow(row(7L, "ML00", 30, 12));
        TSecreCarrier record = new TSecreCarrier();
        record.setId(7L);
        record.setNeedNum(5); // 并账后 应交5 < 已收12，立不起来
        assertEquals(0, service.updateTSecreCarrier(record));
        verify(carrierMapper, never()).update(any(TSecreCarrier.class), any());
    }

    // ---------------- 这一屏 ----------------

    @Test
    public void list_passesWrapperThrough_everyRowFindable() {
        QueryWrapper<TSecreCarrier> w = new QueryWrapper<TSecreCarrier>().eq("del_flag", 0);
        service.selectTSecreCarrierList(w);
        verify(carrierMapper).selectList(w);
    }
}
