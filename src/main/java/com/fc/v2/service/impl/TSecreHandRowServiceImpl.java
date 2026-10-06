package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.secre.SecreHandArbiter;
import com.fc.v2.common.secre.SecreHandMismatch;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.common.secre.SecreHandVerdict;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.model.auto.TSecreCarrier;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.service.ITSecreHandRowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 清退移交行 Service业务层（整批进门形状：一册一次递齐，无单条增删改）。
 *
 * <p>整批怎么进门、单行定去向、重码照面、三数轧账、旧档沉取的判路全在
 * {@link SecreHandArbiter} 这一处，这里只跑腿：点行数、逐行判、落库、回库重数。
 * 行没有绕过判定核自落册的路，也没有自改去向的路。</p>
 *
 * <p>行次规则：进门按落库先后排上号，退回去的行也占行次，所以隔日重查仍是原先
 * 那一个行次，与机关纸面名册行行对得上；行次排定便不再挪。同一批次码第二遍
 * 只照面不翻账——库里一行不添不减，头一遍的去向动不得。</p>
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSecreHandRowServiceImpl implements ITSecreHandRowService {

    @Autowired
    private SecreHandArbiter secreHandArbiter;

    @javax.annotation.Resource
    private TSecreHandRowMapper secreHandRowMapper;

    @Override
    public TSecreHandRow selectTSecreHandRowById(Long id) {
        return this.secreHandRowMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SecreHandReceipt submitBatch(String batchNo, List<TSecreHandRow> rows) {
        // 进门形态钉死：一册一次递齐、整册进门，不接逐行零着递
        if (rows == null || rows.isEmpty()) {
            // 已存在批次空递是撞批的一种，仍只照面；新批次码配空册只给空批回执
            return countReceipt(batchNo);
        }
        // 同一个批次码再递一遍：这一趟只做一件事——与库里已落的挨个照面，
        // 一行不多添、一行不少减，头一遍的去向动不得（不管这趟册多厚）
        if (countBatch(batchNo) > 0) {
            return countReceipt(batchNo);
        }
        // 进门头一步先点行数：越过上限的整册原样退走，库里不许留半行
        if (secreHandArbiter.oversizeBook(rows.size())) {
            SecreHandReceipt rejected = new SecreHandReceipt();
            rejected.setBatchNo(batchNo);
            rejected.setWholeReturned(true);
            rejected.setWholeReason("单册行数越过上限（" + SecreHandArbiter.MAX_ROWS_PER_BOOK
                    + " 行），整册原样退走，库里不留半行");
            rejected.setEmpty(true);
            return rejected;
        }
        // 第一遍：逐行判，行次按进门次序一次性排定（退回也占号，排定不再挪）
        int seq = 0;
        for (TSecreHandRow raw : rows) {
            seq++;
            TSecreHandRow row = raw == null ? new TSecreHandRow() : raw;
            row.setBatchNo(batchNo);
            row.setRowNo(seq);
            row.setDelFlag(0);
            row.setArchiveFlag(SecreHandArbiter.ARCHIVE_CURRENT);
            SecreHandVerdict verdict = secreHandArbiter.judgeRow(row);
            if (verdict.isOk()) {
                // 收下：行由载体册面那一行凭件码关联生成；报文随来的题名、名录代号只作对照
                TSecreCarrier face = verdict.getFace();
                row.setItemCode(face.getCarrierNo());
                row.setStatus(SecreHandArbiter.STATUS_RECEIVED);
                if (!verdict.getMismatches().isEmpty()) {
                    // 对不上的那几行，备注里逐条指名；回执再点名报出去
                    StringBuilder sb = new StringBuilder();
                    for (SecreHandMismatch m : verdict.getMismatches()) {
                        if (sb.length() > 0) {
                            sb.append("；");
                        }
                        sb.append(secreHandArbiter.writeMismatchRemark(m));
                    }
                    row.setRemark(sb.toString());
                }
            } else {
                // 原路退回只退这一行：缘故写在这一行上，别的行不受它带，整册不重报
                row.setStatus(SecreHandArbiter.STATUS_RETURNED);
                row.setRemark(secreHandArbiter.writeReturnRemark(verdict.getField(), verdict.getMsg()));
            }
            this.secreHandRowMapper.insert(row);
        }
        // 过完门要给交代：三数一律回库逐行数出来，不用进门这一遍在内存里累计的数
        return countReceipt(batchNo);
    }

    @Override
    public SecreHandReceipt receipt(String batchNo) {
        return countReceipt(batchNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int revokeBatch(String batchNo) {
        // 撤批：把这一批已落的行撤干净；撤干净才谈得上同批次码重递
        List<TSecreHandRow> rows = loadAll(batchNo);
        int n = 0;
        for (TSecreHandRow r : rows) {
            n += this.secreHandRowMapper.update(null, new UpdateWrapper<TSecreHandRow>()
                    .eq("id", r.getId())
                    .eq("del_flag", 0)
                    .set("del_flag", 1)
                    .set("update_time", new Date()));
        }
        return n;
    }

    @Override
    public List<TSecreHandRow> listBatchRows(String batchNo) {
        return loadAll(batchNo);
    }

    @Override
    public List<TSecreHandRow> listCurrentRows(String batchNo) {
        return this.secreHandRowMapper.selectList(baseWrapper(batchNo)
                .eq("archive_flag", SecreHandArbiter.ARCHIVE_CURRENT)
                .orderByAsc("row_no"));
    }

    @Override
    public List<TSecreHandRow> listArchiveRows(String batchNo) {
        return this.secreHandRowMapper.selectList(baseWrapper(batchNo)
                .eq("archive_flag", SecreHandArbiter.ARCHIVE_OLD)
                .orderByAsc("row_no"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int sinkExpiredRows(int refYear) {
        // 旧档那一层的沉取归判定核判；只朝旧册开——新册 create_time 年份不够走不到跟前
        List<TSecreHandRow> pending = this.secreHandRowMapper.selectList(new QueryWrapper<TSecreHandRow>()
                .eq("del_flag", 0)
                .eq("status", SecreHandArbiter.STATUS_PENDING)
                .eq("archive_flag", SecreHandArbiter.ARCHIVE_CURRENT));
        int sunk = 0;
        for (TSecreHandRow row : pending) {
            if (secreHandArbiter.shouldSink(row, refYear)) {
                this.secreHandRowMapper.update(null, new UpdateWrapper<TSecreHandRow>()
                        .eq("id", row.getId())
                        .eq("del_flag", 0)
                        .set("archive_flag", SecreHandArbiter.ARCHIVE_OLD)
                        .set("update_time", new Date()));
                sunk++;
            }
        }
        return sunk;
    }

    // ---------------- 跑腿：回库重数 ----------------

    /** 回库把这一批的行全部点出来，交判定核逐行数三数。 */
    private SecreHandReceipt countReceipt(String batchNo) {
        List<TSecreHandRow> rows = loadAll(batchNo);
        return secreHandArbiter.buildReceipt(batchNo, rows);
    }

    private List<TSecreHandRow> loadAll(String batchNo) {
        if (batchNo == null) {
            return new ArrayList<TSecreHandRow>();
        }
        return this.secreHandRowMapper.selectList(baseWrapper(batchNo).orderByAsc("row_no"));
    }

    private QueryWrapper<TSecreHandRow> baseWrapper(String batchNo) {
        return new QueryWrapper<TSecreHandRow>()
                .eq("batch_no", batchNo)
                .eq("del_flag", 0);
    }

    private int countBatch(String batchNo) {
        Integer n = this.secreHandRowMapper.selectCount(baseWrapper(batchNo));
        return n == null ? 0 : n;
    }
}
