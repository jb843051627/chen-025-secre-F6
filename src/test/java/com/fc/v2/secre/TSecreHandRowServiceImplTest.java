package com.fc.v2.secre;

import com.fc.v2.common.secre.SecreHandGate;
import com.fc.v2.common.secre.SecreHandIn;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.service.impl.TSecreHandRowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 清退移交行服务单测：服务只跑腿递话，判路全在判定核那一处。
 */
public class TSecreHandRowServiceImplTest {

    private TSecreHandRowServiceImpl service;
    private SecreHandGate gate;

    @BeforeEach
    public void setUp() {
        service = new TSecreHandRowServiceImpl();
        gate = mock(SecreHandGate.class);
        ReflectionTestUtils.setField(service, "secreHandGate", gate);
    }

    @Test
    public void everyAsk_goesToTheOnePlace() {
        SecreHandReceipt receipt = new SecreHandReceipt();
        when(gate.enter(eq("PC001"), anyList())).thenReturn(receipt);
        when(gate.receiptOf("PC001")).thenReturn(receipt);
        when(gate.verify("PC001")).thenReturn(receipt);

        List<SecreHandIn> rows = Collections.emptyList();
        assertSame(receipt, service.enterBatch("PC001", rows));
        verify(gate).enter("PC001", rows);

        assertSame(receipt, service.receiptOf("PC001"));
        verify(gate).receiptOf("PC001");

        assertSame(receipt, service.verify("PC001"));
        verify(gate).verify("PC001");

        service.decideRow("PC001", 2, SecreHandGate.ST_ACCEPTED, null);
        verify(gate).decideRow("PC001", 2, SecreHandGate.ST_ACCEPTED, null);

        service.cancelBatch("PC001");
        verify(gate).cancelBatch("PC001");

        LocalDate today = LocalDate.of(2026, 10, 6);
        service.sinkStale(today);
        verify(gate).sinkStale(today);

        service.listCurrent("PC001");
        verify(gate).listCurrent("PC001");

        service.listArchive("PC001");
        verify(gate).listArchive("PC001");
    }
}
