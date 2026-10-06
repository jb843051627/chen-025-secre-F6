package com.fc.v2.common.secre;

import com.fc.v2.model.auto.TSecreCarrier;

import java.util.ArrayList;
import java.util.List;

/**
 * 清退移交单行判定结果（这一处给出的答案）。
 *
 * <p>立得住的行带着对上的册面与对不上条目（题名、名录代号对不上不挡收，
 * 只点名）；原路退回的行把毛病落在哪一栏、缘故是什么一并带出，
 * 缘故只写在这一行上，不带累别行。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandVerdict {

    /** 这一行收不收得下 */
    private boolean ok;

    /** 毛病落在哪一栏（qty/qualiNum/itemCode/handKind），收得下时为空 */
    private String field;

    /** 原路退回的缘故（写在这一行上） */
    private String msg;

    /** 原报行次 */
    private Integer rowNo;

    /** 载体件码 */
    private String itemCode;

    /** 凭件码对上的那一行载体册面；收得下时不为空 */
    private TSecreCarrier face;

    /** 报文与册面对不上的条目（不挡收，只点名） */
    private List<SecreHandMismatch> mismatches = new ArrayList<SecreHandMismatch>();

    public static SecreHandVerdict ok(TSecreCarrier face, List<SecreHandMismatch> mismatches) {
        SecreHandVerdict v = new SecreHandVerdict();
        v.ok = true;
        v.face = face;
        if (mismatches != null) {
            v.mismatches = mismatches;
        }
        return v;
    }

    public static SecreHandVerdict fail(String field, String msg) {
        SecreHandVerdict v = new SecreHandVerdict();
        v.ok = false;
        v.field = field;
        v.msg = msg;
        return v;
    }

    public boolean isOk() {
        return ok;
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public TSecreCarrier getFace() {
        return face;
    }

    public void setFace(TSecreCarrier face) {
        this.face = face;
    }

    public List<SecreHandMismatch> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<SecreHandMismatch> mismatches) {
        this.mismatches = mismatches;
    }
}
