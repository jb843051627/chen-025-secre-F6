package com.fc.v2.secre;

import com.fc.v2.common.secre.SecreHandGate;
import com.fc.v2.common.secre.SecreHandIn;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.common.secre.SecreHandVerdict;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 过门判定核（这一处）单测：行上带什么、只退这一行、重递照面、册首三数、旧档那一层。
 *
 * <p>跑腿层（库口）换成内存假库，判路走的是真身。</p>
 */
public class SecreHandGateTest {

    /** 假库：跑腿层全换成内存账，判路不变 */
    static class FakeGate extends SecreHandGate {
        final List<TSecreHandRow> store = new ArrayList<TSecreHandRow>();
        final Map<String, TSecreCarrier> carriers = new HashMap<String, TSecreCarrier>();
        long idSeq = 100L;

        @Override
        protected List<TSecreHandRow> findBatchRows(String batchNo) {
            return store.stream()
                    .filter(r -> batchNo.equals(r.getBatchNo()) && Integer.valueOf(0).equals(r.getDelFlag()))
                    .sorted(Comparator.comparing(TSecreHandRow::getRowNo,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());
        }

        @Override
        protected TSecreHandRow findBatchRow(String batchNo, Integer rowNo) {
            return store.stream()
                    .filter(r -> batchNo.equals(r.getBatchNo()) && Objects.equals(r.getRowNo(), rowNo)
                            && Integer.valueOf(0).equals(r.getDelFlag()))
                    .findFirst().orElse(null);
        }

        @Override
        protected TSecreCarrier findCarrier(String itemCode) {
            if (itemCode == null || itemCode.trim().isEmpty()) {
                return null;
            }
            return carriers.get(itemCode);
        }

        @Override
        protected int insertRow(TSecreHandRow row) {
            row.setId(++idSeq);
            if (row.getCreateTime() == null) {
                row.setCreateTime(new Date());
            }
            store.add(row);
            return 1;
        }

        @Override
        protected int updateRow(TSecreHandRow row) {
            for (TSecreHandRow r : store) {
                if (Objects.equals(r.getId(), row.getId())) {
                    if (row.getStatus() != null) {
                        r.setStatus(row.getStatus());
                    }
                    if (row.getRemark() != null) {
                        r.setRemark(row.getRemark());
                    }
                    r.setUpdateTime(new Date());
                    return 1;
                }
            }
            return 0;
        }

        @Override
        protected int softDeleteBatch(String batchNo) {
            int n = 0;
            for (TSecreHandRow r : store) {
                if (batchNo.equals(r.getBatchNo()) && Integer.valueOf(0).equals(r.getDelFlag())) {
                    r.setDelFlag(1);
                    n++;
                }
            }
            return n;
        }

        @Override
        protected int markSunk(LocalDate cutoff) {
            int n = 0;
            Date bound = Timestamp.valueOf(cutoff.atStartOfDay());
            for (TSecreHandRow r : store) {
                if (Integer.valueOf(0).equals(r.getDelFlag()) && Integer.valueOf(0).equals(r.getArchFlag())
                        && Integer.valueOf(SecreHandGate.ST_PENDING).equals(r.getStatus())
                        && r.getCreateTime() != null && r.getCreateTime().before(bound)) {
                    r.setArchFlag(1);
                    n++;
                }
            }
            return n;
        }

        @Override
        protected List<TSecreHandRow> listRows(String batchNo, int archFlag) {
            return store.stream()
                    .filter(r -> batchNo.equals(r.getBatchNo()) && Integer.valueOf(0).equals(r.getDelFlag())
                            && Integer.valueOf(archFlag).equals(r.getArchFlag()))
                    .sorted(Comparator.comparing(TSecreHandRow::getRowNo))
                    .collect(Collectors.toList());
        }
    }

    private FakeGate gate;

    private static TSecreCarrier carrier(String code, String content, String bookNo) {
        TSecreCarrier c = new TSecreCarrier();
        c.setCarrierNo(code);
        c.setContent(content);
        c.setBookNo(bookNo);
        c.setDelFlag(0);
        return c;
    }

    private static SecreHandIn in(String code, String qty, String quali, String kind) {
        return new SecreHandIn(code,
                qty == null ? null : new BigDecimal(qty),
                quali == null ? null : new BigDecimal(quali),
                kind);
    }

    @BeforeEach
    public void setUp() {
        gate = new FakeGate();
        gate.carriers.put("JM001", carrier("JM001", "机密图纸一袋", "ML00"));
        gate.carriers.put("JM002", carrier("JM002", "秘密台账两册", "ML00"));
    }

    // ---------------- 第一桩：行上带什么 ----------------

    @Test
    public void enter_landsValidRowsAsPending_inOrder() {
        SecreHandReceipt receipt = gate.enter("PC001", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "1", SecreHandGate.KIND_ABOVE)));
        assertEquals(SecreHandReceipt.OUTCOME_ACCEPT, receipt.getOutcome());
        assertEquals(2, receipt.getReported());
        assertEquals(2, receipt.getLanded());
        assertEquals(0, receipt.getReturned());
        assertTrue(receipt.isBalanced());
        assertTrue(receipt.isConsistent());
        // 行次归系统管：进门按落库先后排上号
        assertEquals(Integer.valueOf(1), gate.store.get(0).getRowNo());
        assertEquals(Integer.valueOf(2), gate.store.get(1).getRowNo());
        // 落库先挂未勾，等勾
        assertEquals(Integer.valueOf(SecreHandGate.ST_PENDING), gate.store.get(0).getStatus());
        assertEquals(Integer.valueOf(0), gate.store.get(0).getArchFlag());
        // 一行挂两个数，一并落库
        assertEquals(0, new BigDecimal("3").compareTo(gate.store.get(0).getQty()));
        assertEquals(0, new BigDecimal("1").compareTo(gate.store.get(1).getQualiNum()));
    }

    @Test
    public void enter_missingNumber_returned_nobodyPatches() {
        SecreHandReceipt receipt = gate.enter("PC002", Arrays.asList(
                in("JM001", null, "3", SecreHandGate.KIND_LOCAL),
                in("JM001", "3", null, SecreHandGate.KIND_LOCAL)));
        assertEquals(2, receipt.getReturned());
        assertEquals(0, receipt.getLanded());
        assertTrue(receipt.getReturnedRows().get(0).getReason().contains("应交件数缺报"));
        assertTrue(receipt.getReturnedRows().get(1).getReason().contains("核收合格件数缺报"));
        // 缺数的那一行，库里那格也不许旁人替它补一个上去
        assertNull(gate.store.get(0).getQty());
        assertNull(gate.store.get(1).getQualiNum());
    }

    @Test
    public void sameCarrier_twoRows_twoAccounts() {
        gate.enter("PC003", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM001", "5", "5", SecreHandGate.KIND_LOCAL)));
        // 同一件载体占两行，两行两本账：收下头一行不捎带第二行
        SecreHandVerdict v1 = gate.decideRow("PC003", 1, SecreHandGate.ST_ACCEPTED, null);
        assertTrue(v1.isOk());
        assertEquals(Integer.valueOf(SecreHandGate.ST_ACCEPTED), gate.findBatchRow("PC003", 1).getStatus());
        assertEquals(Integer.valueOf(SecreHandGate.ST_PENDING), gate.findBatchRow("PC003", 2).getStatus());
        // 退回第二行也不牵扯头一行
        SecreHandVerdict v2 = gate.decideRow("PC003", 2, SecreHandGate.ST_RETURNED, "核收不合");
        assertTrue(v2.isOk());
        assertEquals(Integer.valueOf(SecreHandGate.ST_RETURNED), gate.findBatchRow("PC003", 2).getStatus());
        assertEquals(Integer.valueOf(SecreHandGate.ST_ACCEPTED), gate.findBatchRow("PC003", 1).getStatus());
    }

    // ---------------- 第二桩：什么情形只退这一行 ----------------

    @Test
    public void enter_rowFaults_returnOnlyThatRow() {
        StringBuilder longCode = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            longCode.append('X');
        }
        SecreHandReceipt receipt = gate.enter("PC004", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),       // 1 立得住
                in("JM001", "0", "0", SecreHandGate.KIND_LOCAL),       // 2 报成零
                in("JM001", "-2", "0", SecreHandGate.KIND_LOCAL),      // 3 报成负数
                in("JM001", "10000", "0", SecreHandGate.KIND_LOCAL),   // 4 压过核收上限
                in(null, "1", "1", SecreHandGate.KIND_LOCAL),          // 5 件码空着
                in(longCode.toString(), "1", "1", SecreHandGate.KIND_LOCAL), // 6 件码长过那一把尺
                in("JM999", "1", "1", SecreHandGate.KIND_LOCAL),       // 7 件码与册面对不上
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));     // 8 立得住
        assertEquals(8, receipt.getReported());
        assertEquals(2, receipt.getLanded());
        assertEquals(6, receipt.getReturned());
        assertTrue(receipt.isBalanced());
        // 退这一行，缘故写在这一行上；别的行不受它带
        List<SecreHandReceipt.RowNote> back = receipt.getReturnedRows();
        assertEquals(6, back.size());
        assertEquals(2, back.get(0).getRowNo());
        assertTrue(back.get(0).getReason().contains("报成零"));
        assertTrue(back.get(1).getReason().contains("报成负数"));
        assertTrue(back.get(2).getReason().contains("核收上限"));
        assertTrue(back.get(3).getReason().contains("件码空着"));
        assertTrue(back.get(4).getReason().contains("长过那一把尺"));
        assertTrue(back.get(5).getReason().contains("对不上"));
        // 立得住的两行照旧落地，行次还是原先那一个
        assertEquals(Integer.valueOf(SecreHandGate.ST_PENDING), gate.findBatchRow("PC004", 1).getStatus());
        assertEquals(Integer.valueOf(SecreHandGate.ST_PENDING), gate.findBatchRow("PC004", 8).getStatus());
        assertEquals(Integer.valueOf(SecreHandGate.ST_RETURNED), gate.findBatchRow("PC004", 2).getStatus());
        // 没落成的那几行，纸面行次随回执带出去
        assertEquals(7, back.get(5).getRowNo());
    }

    @Test
    public void enter_overRowLimit_wholeBookRefused_nothingStored() {
        gate.setMaxRowsPerBook(3);
        List<SecreHandIn> rows = Arrays.asList(
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL),
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL),
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL),
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL));
        SecreHandReceipt receipt = gate.enter("PC005", rows);
        assertEquals(SecreHandReceipt.OUTCOME_REFUSED, receipt.getOutcome());
        assertEquals(4, receipt.getReported());
        assertEquals(0, receipt.getLanded());
        assertEquals(4, receipt.getReturned());
        // 库里不许留半行
        assertTrue(gate.store.isEmpty());
        // 退走重报：行数点齐了再递，同一批次码照常进门
        SecreHandReceipt again = gate.enter("PC005", rows.subList(0, 3));
        assertEquals(SecreHandReceipt.OUTCOME_ACCEPT, again.getOutcome());
        assertEquals(3, again.getLanded());
    }

    @Test
    public void enter_blankBatchNo_refused() {
        SecreHandReceipt receipt = gate.enter("  ", Collections.singletonList(
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL)));
        assertEquals(SecreHandReceipt.OUTCOME_REFUSED, receipt.getOutcome());
        assertTrue(gate.store.isEmpty());
    }

    // ---------------- 第三桩：同一个批次码再递一遍 ----------------

    @Test
    public void duplicateBatch_onlyFacesOff_neverRewrites() {
        List<SecreHandIn> rows = Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_ABOVE));
        SecreHandReceipt first = gate.enter("PC006", rows);
        assertEquals(2, first.getLanded());
        int storeSize = gate.store.size();
        // 同一批次码再递一遍：只照面，不翻账
        SecreHandReceipt again = gate.enter("PC006", rows);
        assertEquals(SecreHandReceipt.OUTCOME_FACE, again.getOutcome());
        assertEquals(storeSize, gate.store.size());
        assertEquals(2, again.getFaceOffs().size());
        assertEquals("对得上", again.getFaceOffs().get(0).getResult());
        assertEquals("对得上", again.getFaceOffs().get(1).getResult());
        // 三数照旧，与头一遍同一回算
        assertEquals(first.getReported(), again.getReported());
        assertEquals(first.getLanded(), again.getLanded());
        assertEquals(first.getReturned(), again.getReturned());
        // 头一遍落成的去向动它不得
        assertEquals(Integer.valueOf(SecreHandGate.ST_PENDING), gate.findBatchRow("PC006", 1).getStatus());
    }

    @Test
    public void faceOff_namesDiffMoreLess() {
        gate.enter("PC007", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));
        // 第二遍：第2行应交件数改了口，又多递一行
        SecreHandReceipt again = gate.enter("PC007", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "9", "2", SecreHandGate.KIND_LOCAL),
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL)));
        assertEquals(SecreHandReceipt.OUTCOME_FACE, again.getOutcome());
        assertEquals(3, again.getFaceOffs().size());
        assertEquals("对得上", again.getFaceOffs().get(0).getResult());
        assertTrue(again.getFaceOffs().get(1).getResult().contains("对不上"));
        assertTrue(again.getFaceOffs().get(1).getResult().contains("应交件数"));
        assertTrue(again.getFaceOffs().get(2).getResult().contains("多递"));
        // 一行不多添：库里的账没动
        assertEquals(2, gate.store.size());
        assertEquals(0, new BigDecimal("2").compareTo(gate.findBatchRow("PC007", 2).getQty()));
        // 少递也指名
        SecreHandReceipt third = gate.enter("PC007", Collections.singletonList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL)));
        assertTrue(third.getFaceOffs().get(1).getResult().contains("少递"));
        assertEquals(2, gate.store.size());
    }

    @Test
    public void cancelBatch_thenReentry_allowed() {
        gate.enter("PC008", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));
        // 真要改，先办撤批
        assertEquals(2, gate.cancelBatch("PC008"));
        // 撤不干净（没办撤批）时，重递只照面
        SecreHandReceipt fresh = gate.enter("PC008", Collections.singletonList(
                in("JM001", "8", "8", SecreHandGate.KIND_LOCAL)));
        assertEquals(SecreHandReceipt.OUTCOME_ACCEPT, fresh.getOutcome());
        assertEquals(1, fresh.getLanded());
        assertEquals(Integer.valueOf(1), gate.findBatchRow("PC008", 1).getRowNo());
    }

    @Test
    public void returnedRow_keepsItsPaperRowNo() {
        gate.enter("PC009", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM001", "0", "0", SecreHandGate.KIND_LOCAL)));
        // 头一日退回去的行，隔一日再查仍是原先那一个行次
        SecreHandReceipt receipt = gate.receiptOf("PC009");
        assertEquals(1, receipt.getReturnedRows().size());
        assertEquals(2, receipt.getReturnedRows().get(0).getRowNo());
        assertTrue(receipt.getReturnedRows().get(0).getReason().contains("报成零"));
        // 第二遍照面，行次也不挪
        SecreHandReceipt again = gate.enter("PC009", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM001", "0", "0", SecreHandGate.KIND_LOCAL)));
        assertEquals(2, again.getReturnedRows().get(0).getRowNo());
    }

    // ---------------- 第四桩：过完门要给交代 ----------------

    @Test
    public void receipt_emptyBatch_noAcceptance() {
        SecreHandReceipt receipt = gate.enter("PC010", Arrays.asList(
                in("JM001", "0", "0", SecreHandGate.KIND_LOCAL),
                in("JM999", "1", "1", SecreHandGate.KIND_LOCAL)));
        // 一行都没收着的册，回执只写空批，不出收讫
        assertEquals(SecreHandReceipt.OUTCOME_EMPTY, receipt.getOutcome());
        assertFalse(receipt.isAccepted());
        assertEquals("空批", receipt.summary());
        assertEquals(2, receipt.getReported());
        assertEquals(0, receipt.getLanded());
        assertEquals(2, receipt.getReturned());
        assertTrue(receipt.isBalanced());
    }

    @Test
    public void receipt_emptySubmission_isEmptyBatch() {
        SecreHandReceipt receipt = gate.enter("PC011", Collections.<SecreHandIn>emptyList());
        assertEquals(SecreHandReceipt.OUTCOME_EMPTY, receipt.getOutcome());
        assertEquals(0, receipt.getReported());
        assertTrue(gate.store.isEmpty());
    }

    @Test
    public void receipt_countedFromStore_notFromScreen() {
        gate.enter("PC012", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM001", "0", "0", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));
        // 屏上先亮的那份不作数：回库一行一行数出来的才是回执
        SecreHandReceipt receipt = gate.receiptOf("PC012");
        assertEquals(3, receipt.getReported());
        assertEquals(2, receipt.getLanded());
        assertEquals(1, receipt.getReturned());
        assertTrue(receipt.isConsistent());
        assertEquals("收讫：原报3行，落地2行，原路退回1行", receipt.summary());
    }

    @Test
    public void verify_namesRowsThatDontAddUp() {
        gate.enter("PC013", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));
        // 有人绕过这一处在库底塞了第四个字样：互证互对要点名报出去
        gate.store.get(0).setStatus(9);
        SecreHandReceipt receipt = gate.verify("PC013");
        assertFalse(receipt.isConsistent());
        assertFalse(receipt.isBalanced());
        assertEquals(1, receipt.getOffRows().size());
        assertTrue(receipt.getOffRows().get(0).contains("第1行"));
        assertTrue(receipt.getOffRows().get(0).contains("落不进三个字样"));
    }

    // ---------------- 第五桩：旧的那一层 ----------------

    @Test
    public void sinkStale_onlyOldPendingSink_currentListDropsThem() {
        gate.enter("PC014", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL),
                in("JM001", "1", "1", SecreHandGate.KIND_LOCAL)));
        // 第2行勾成收下，第3行退回：沉旧档只找去向空悬的
        gate.decideRow("PC014", 2, SecreHandGate.ST_ACCEPTED, null);
        gate.decideRow("PC014", 3, SecreHandGate.ST_RETURNED, "核收不合");
        // 行落库那一刻：第1行是前年冬落的（连着满两个年度），第2、3行也是
        Date old = Timestamp.valueOf(LocalDate.of(2023, 6, 1).atStartOfDay());
        for (TSecreHandRow r : gate.store) {
            r.setCreateTime(old);
        }
        int sunk = gate.sinkStale(LocalDate.of(2026, 10, 6));
        assertEquals(1, sunk);
        // 现行名单不再列它，存量里照旧翻得着、一条不少
        assertEquals(2, gate.listCurrent("PC014").size());
        assertEquals(1, gate.listArchive("PC014").size());
        assertEquals(Integer.valueOf(1), gate.listArchive("PC014").get(0).getRowNo());
        // 册首三数照原样轧得出：沉旧档不动账
        SecreHandReceipt receipt = gate.receiptOf("PC014");
        assertEquals(3, receipt.getReported());
        assertEquals(2, receipt.getLanded());
        assertEquals(1, receipt.getReturned());
    }

    @Test
    public void sinkStale_newBooksNeverReachIt() {
        gate.enter("PC015", Collections.singletonList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL)));
        // 今年才落的行，这条路走不到它跟前
        assertEquals(0, gate.sinkStale(LocalDate.of(2026, 10, 6)));
        assertEquals(1, gate.listCurrent("PC015").size());
        assertEquals(0, gate.listArchive("PC015").size());
    }

    @Test
    public void registerIsAuthoritative_mismatchNamed() {
        SecreHandIn in = in("JM001", "3", "3", SecreHandGate.KIND_LOCAL);
        in.setTitle("报文里写错的题名");
        in.setBookNo("ML99");
        SecreHandReceipt receipt = gate.enter("PC016", Collections.singletonList(in));
        // 行照落，对不上的那一行指名报出，以册面为准
        assertEquals(1, receipt.getLanded());
        assertEquals(1, receipt.getMismatches().size());
        SecreHandReceipt.Mismatch m = receipt.getMismatches().get(0);
        assertEquals(1, m.getRowNo());
        assertTrue(m.getDetail().contains("题名对不上"));
        assertTrue(m.getDetail().contains("名录代号对不上"));
        assertTrue(m.getDetail().contains("以册面为准"));
    }

    @Test
    public void carrierMatched_onlyByItemCode_titleDoesNotCount() {
        // 拿题名去凑的：题名一字不差，件码对不上，统统算没对上
        SecreHandIn in = in("JM999", "1", "1", SecreHandGate.KIND_LOCAL);
        in.setTitle("机密图纸一袋");
        SecreHandReceipt receipt = gate.enter("PC017", Collections.singletonList(in));
        assertEquals(0, receipt.getLanded());
        assertEquals(1, receipt.getReturned());
        assertTrue(receipt.getReturnedRows().get(0).getReason().contains("对不上"));
    }

    // ---------------- 单行定去向的门规 ----------------

    @Test
    public void decideRow_rules() {
        gate.enter("PC018", Arrays.asList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL),
                in("JM002", "2", "2", SecreHandGate.KIND_LOCAL)));
        // 落不进第四个字样
        assertFalse(gate.decideRow("PC018", 1, 3, null).isOk());
        assertFalse(gate.decideRow("PC018", 1, SecreHandGate.ST_PENDING, null).isOk());
        // 行次空着、库里没有的行
        assertFalse(gate.decideRow("PC018", null, SecreHandGate.ST_ACCEPTED, null).isOk());
        assertFalse(gate.decideRow("PC018", 9, SecreHandGate.ST_ACCEPTED, null).isOk());
        // 退回这一行，缘故得写在这一行上
        assertFalse(gate.decideRow("PC018", 1, SecreHandGate.ST_RETURNED, "  ").isOk());
        SecreHandVerdict back = gate.decideRow("PC018", 1, SecreHandGate.ST_RETURNED, "核收不合");
        assertTrue(back.isOk());
        assertEquals("核收不合", gate.findBatchRow("PC018", 1).getRemark());
        // 头一遍落成的去向动它不得
        SecreHandVerdict again = gate.decideRow("PC018", 1, SecreHandGate.ST_ACCEPTED, null);
        assertFalse(again.isOk());
        assertTrue(again.getMsg().contains("动它不得"));
        // 收下那一行
        assertTrue(gate.decideRow("PC018", 2, SecreHandGate.ST_ACCEPTED, null).isOk());
        assertEquals(Integer.valueOf(SecreHandGate.ST_ACCEPTED), gate.findBatchRow("PC018", 2).getStatus());
    }

    @Test
    public void decideRow_archivedRow_refused() {
        gate.enter("PC019", Collections.singletonList(
                in("JM001", "3", "3", SecreHandGate.KIND_LOCAL)));
        gate.store.get(0).setCreateTime(Timestamp.valueOf(LocalDate.of(2023, 1, 1).atStartOfDay()));
        gate.sinkStale(LocalDate.of(2026, 10, 6));
        SecreHandVerdict v = gate.decideRow("PC019", 1, SecreHandGate.ST_ACCEPTED, null);
        assertFalse(v.isOk());
        assertTrue(v.getMsg().contains("旧档"));
    }
}
