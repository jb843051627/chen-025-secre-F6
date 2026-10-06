package com.fc.v2.common.secre;

import java.util.ArrayList;
import java.util.List;

/**
 * 过完门的交代：清退移交回执。
 *
 * <p>回执只留三个数——原报几行、落地几行、原路退回几行；退的与落的合起来
 * 必须等于原报。三个数不许先亮在屏上，一律回库一行一行数出来，屏上这份
 * 只是复述。退回去的行也把机关纸面的原报行次带出去，好与机关那本册子
 * 行行对得上。</p>
 *
 * <p>一册里一行都没收着（一册空递、或行行退回）的那种，回执只写「空批」，
 * 不出收讫——收讫是有行数落了地的册才说的话。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreHandReceipt {

    /** 批次码 */
    private String batchNo;

    /** 原报几行（这一趟递来几行） */
    private int reported;

    /** 落地几行（回库里逐行数出来的已收下，不含退回） */
    private int landed;

    /** 原路退回几行（回库里逐行数出来的已退回） */
    private int returned;

    /** 空批：一行都没收着（含一册空递） */
    private boolean empty;

    /** 整册原样退走：进门点行数越过单册上限，门都没进（库里不留半行，不走逐行三数账） */
    private boolean wholeReturned;

    /** 整册退走的缘故 */
    private String wholeReason;

    /** 收讫：有行数落了地才说的话；空批不出收讫 */
    private boolean received;

    /** 退回明细：每行带原报行次与件码，与机关纸面名册行行对得上 */
    private List<SecreHandReturned> returnedRows = new ArrayList<SecreHandReturned>();

    /** 报文与册面对不上的行：逐条点名，点到原报行次与件码 */
    private List<SecreHandMismatch> mismatches = new ArrayList<SecreHandMismatch>();

    /**
     * 回库逐行数完后核一遍账：退的与落的合起来须等于原报。
     *
     * @return 三数轧得平为 true
     */
    public boolean balances() {
        return landed + returned == reported;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
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

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    public boolean isWholeReturned() {
        return wholeReturned;
    }

    public void setWholeReturned(boolean wholeReturned) {
        this.wholeReturned = wholeReturned;
    }

    public String getWholeReason() {
        return wholeReason;
    }

    public void setWholeReason(String wholeReason) {
        this.wholeReason = wholeReason;
    }

    public boolean isReceived() {
        return received;
    }

    public void setReceived(boolean received) {
        this.received = received;
    }

    public List<SecreHandReturned> getReturnedRows() {
        return returnedRows;
    }

    public void setReturnedRows(List<SecreHandReturned> returnedRows) {
        this.returnedRows = returnedRows;
    }

    public List<SecreHandMismatch> getMismatches() {
        return mismatches;
    }

    public void setMismatches(List<SecreHandMismatch> mismatches) {
        this.mismatches = mismatches;
    }
}
