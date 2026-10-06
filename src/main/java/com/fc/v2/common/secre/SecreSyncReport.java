package com.fc.v2.common.secre;

import java.util.ArrayList;
import java.util.List;

/**
 * 上级名录下发合并报告：收了几条、新入几册、对不上几条，逐条点名到代号。
 *
 * <p>冲突不许悄悄盖掉——拿一份悄悄盖掉另一份不算交代，对不上的都在
 * {@link #conflicts} 里摆着，缘由同时落到本地名录那一份的备注里。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
public class SecreSyncReport {

    /** 这一份下发一共收了几条 */
    private int total;

    /** 本地没有、照下发新入底册几册 */
    private int inserted;

    /** 代号空着、立不进册几条 */
    private int skipped;

    /** 对不上的条目，逐条点名到代号 */
    private List<SecreSyncConflict> conflicts = new ArrayList<SecreSyncConflict>();

    public void incrTotal() {
        this.total++;
    }

    public void incrInserted() {
        this.inserted++;
    }

    public void incrSkipped() {
        this.skipped++;
    }

    public void addConflict(String bookNo, String reason) {
        this.conflicts.add(new SecreSyncConflict(bookNo, reason));
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public List<SecreSyncConflict> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<SecreSyncConflict> conflicts) {
        this.conflicts = conflicts;
    }
}
