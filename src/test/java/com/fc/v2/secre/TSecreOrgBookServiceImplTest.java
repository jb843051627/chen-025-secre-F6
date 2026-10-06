package com.fc.v2.secre;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreBookArbiter;
import com.fc.v2.mapper.auto.TSecreOrgBookMapper;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.impl.TSecreOrgBookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 名录服务单测：本机关自录的来路与代号底线。
 */
public class TSecreOrgBookServiceImplTest {

    private TSecreOrgBookServiceImpl service;
    private TSecreOrgBookMapper orgBookMapper;

    @BeforeEach
    public void setUp() {
        service = new TSecreOrgBookServiceImpl();
        orgBookMapper = mock(TSecreOrgBookMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", orgBookMapper);
        ReflectionTestUtils.setField(service, "secreBookArbiter", new SecreBookArbiter());
        when(orgBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(0);
        when(orgBookMapper.insert(any(TSecreOrgBook.class))).thenReturn(1);
        when(orgBookMapper.update(any(TSecreOrgBook.class), any())).thenReturn(1);
    }

    @Test
    public void insert_localEntry_marksSyncKindLocal() {
        TSecreOrgBook record = new TSecreOrgBook();
        record.setBookNo("ML08");
        record.setBookName("本机关自录单位");
        record.setSyncKind("上级名录下发"); // 自录口进来的一律改记本机关自录
        assertEquals(1, service.insertTSecreOrgBook(record));
        ArgumentCaptor<TSecreOrgBook> cap = ArgumentCaptor.forClass(TSecreOrgBook.class);
        verify(orgBookMapper).insert(cap.capture());
        assertEquals(SecreBookArbiter.SYNC_LOCAL, cap.getValue().getSyncKind());
        assertEquals(Integer.valueOf(0), cap.getValue().getStatus());
    }

    @Test
    public void insert_dupBookNo_rejected() {
        when(orgBookMapper.selectCount(any(QueryWrapper.class))).thenReturn(1);
        TSecreOrgBook record = new TSecreOrgBook();
        record.setBookNo("ML00");
        assertEquals(0, service.insertTSecreOrgBook(record));
        verify(orgBookMapper, never()).insert(any(TSecreOrgBook.class));
    }

    @Test
    public void insert_blankBookNo_rejected() {
        assertEquals(0, service.insertTSecreOrgBook(new TSecreOrgBook()));
        verify(orgBookMapper, never()).insert(any(TSecreOrgBook.class));
    }

    @Test
    public void update_neverTouchesBookNo() {
        TSecreOrgBook record = new TSecreOrgBook();
        record.setId(9L);
        record.setBookNo("ML09-改名"); // 代号是挂行的底，改口不许跟着改
        record.setBookName("换了隶属的单位");
        record.setStatus(1); // 记已撤出：只挡新行，不抹旧账
        assertEquals(1, service.updateTSecreOrgBook(record));
        ArgumentCaptor<TSecreOrgBook> cap = ArgumentCaptor.forClass(TSecreOrgBook.class);
        verify(orgBookMapper).update(cap.capture(), any());
        assertNull(cap.getValue().getBookNo());
        assertEquals(Integer.valueOf(1), cap.getValue().getStatus());
    }
}
