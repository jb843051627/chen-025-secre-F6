package com.fc.v2.common.secre;

import java.util.ArrayList;
import java.util.List;

/**
 * 清退移交回执——过完门要给的那份交代。
 *
 * <p>回执只留三个数：原报几行、落地几行、原路退回几行；退的与落的合起来须等于
 * 原报那个数。三个数一律回库一行一行数出来，先亮在屏上的那份不作数，屏上那份
 * 只是这一份的复述。没落成的那几行把纸面行次随回执带出去，好让机关那本册子
 * 行行对得上；册面对不上的那几行也指名报出。</p>
 *
 * <p>一册里一行都没收着的那种，回执只写「空批」两字，不出收讫——收讫是有行数
 * 的册才说的话。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandReceipt {

    /** 交代：收讫（有行数落库的册才说的话） */
    public static final String OUTCOME_ACCEPT = "收讫";
    /** 交代：空批（一行都没收着，不出收讫） */
    public static final String OUTCOME_EMPTY = "空批";
    /** 交代：整册退回（行数越上限或批次码空着，库里不许留半行） */
    public static final String OUTCOME_REFUSED = "整册退回";
    /** 交代：照面（同一批次码再递一遍，只照面不翻账） */
    public static final String OUTCOME_FACE = "照面";

    /** 批次码 */
    private String batchNo;

    /** 这一趟的交代：收讫／空批／整册退回／照面 */
    private String outcome;

    /** 原报几行 */
    private int reported;

    /** 落地几行 */
    private int landed;

    /** 原路退回几行 */
    private int returned;

    /** 门级交代的话（整册退回的缘由、照面的交代） */
    private String note;

    /** 没落成的那几行：纸面行次随回执带出去，缘故写在这一行上 */
    private List<RowNote> returnedRows = new ArrayList<RowNote>();

    /** 册面对不上的那几行（指名报出，以册面为准） */
    private List<Mismatch> mismatches = new ArrayList<Mismatch>();

    /** 照面明细（同一批次码再递那一趟，挨个照面的结果） */
    private List<FaceOff> faceOffs = new ArrayList<FaceOff>();

    /** 互证互对对不住的那几行（点名报出去） */
    private List<String> offRows = new ArrayList<String>();

    /** 退的与落的合起来等不等于原报那个数（且没有对不住的行） */
    private boolean consistent;

    /** 退的与落的合起来须等于原报那个数 */
    public boolean isBalanced() {
        return landed + returned == reported;
    }

    /** 这一册算不算收讫（有行数落库才说的话） */
    public boolean isAccepted() {
        return OUTCOME_ACCEPT.equals(outcome);
    }

    /** 回执上说给人听的那一句 */
    public String summary() {
        if (OUTCOME_EMPTY.equals(outcome)) {
            return "空批";
        }
        if (OUTCOME_REFUSED.equals(outcome)) {
            return "整册退回：" + (note == null ? "" : note);
        }
        if (OUTCOME_FACE.equals(outcome)) {
            return "照面：" + (note == null ? "" : note)
                    + "（原报" + reported + "行，落地" + landed + "行，原路退回" + returned + "行）";
        }
        return "收讫：原报" + reported + "行，落地" + landed + "行，原路退回" + returned + "行";
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public int getReported() {
        return reported;
    }

    public void setReported(int reported) {
        this.reported = reported;
    }

    public int getLanded() {
        return landed;
    }

    public void setLanded(int landed) {
        this.landed = landed;
    }

    public int getReturned() {
        return returned;
    }

    public void setReturned(int returned) {
        this.returned = returned;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<RowNote> getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(List<RowNote> returnedRows) {
        this.returnedRows = returnedRows;
    }

    public List<Mismatch> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<Mismatch> mismatches) {
        this.mismatches = mismatches;
    }

    public List<FaceOff> getFaceOffs() {
        return faceOffs;
    }

    public void setFaceOffs(List<FaceOff> faceOffs) {
        this.faceOffs = faceOffs;
    }

    public List<String> getOffRows() {
        return offRows;
    }

    public void setOffRows(List<String> offRows) {
        this.offRows = offRows;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    /**
     * 没落成的一行：纸面行次随回执带出去，缘故写在这一行上。
     */
    public static class RowNote {
        /** 纸面行次（进门按落库先后排上的号，排定不再挪） */
        private int rowNo;
        /** 件码（报文里递来的那串，对不上册面的也照原样带出去） */
        private String itemCode;
        /** 退这一行的缘故 */
        private String reason;

        public RowNote(int rowNo, String itemCode, String reason) {
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.reason = reason;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * 册面对不上的一行（指名报出）：报文里那串只作对照，以册面为准。
     */
    public static class Mismatch {
        /** 纸面行次 */
        private int rowNo;
        /** 件码 */
        private String itemCode;
        /** 哪一栏对不上、两头各是什么、以哪一份作准 */
        private String detail;

        public Mismatch(int rowNo, String itemCode, String detail) {
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.detail = detail;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public String getDetail() {
            return detail;
        }
    }

    /**
     * 照面的一行：同一批次码再递一遍时，递来的行与库里已落的挨个照面的结果。
     */
    public static class FaceOff {
        /** 照到第几行（纸面行次） */
        private int rowNo;
        /** 照面结果：对得上／对不上哪几栏／多递／少递 */
        private String result;

        public FaceOff(int rowNo, String result) {
            this.rowNo = rowNo;
            this.result = result;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getResult() {
            return result;
        }
    }
}
