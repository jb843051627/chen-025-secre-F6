package com.fc.v2.service.impl;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.secre.SecreHandGate;
import com.fc.v2.common.secre.SecreHandIn;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.common.secre.SecreHandVerdict;
import com.fc.v2.mapper.auto.TSecreHandRowMapper;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.service.ITSecreHandRowService;

/**
 * 清退移交行 Service业务层处理（batch-process 形状：整册进门，无单条增删改）
 *
 * <p>判路全在 {@link SecreHandGate} 这一处：整批进门、单行定去向、重递照面、册首
 * 三数、撤批、旧档沉取，一律到那里去对答案；这里只跑腿递话，不另起判路，行没有
 * 绕过它自落册的路，也没有自改去向的路。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Service
public class TSecreHandRowServiceImpl implements ITSecreHandRowService {

    @javax.annotation.Resource
    private TSecreHandRowMapper secreHandRowMapper;

    @Autowired
    private SecreHandGate secreHandGate;

    @Override
    public TSecreHandRow selectTSecreHandRowById(Long id) {
        return this.secreHandRowMapper.selectOne(new QueryWrapper<TSecreHandRow>()
                .eq("id", id)
                .eq("del_flag", 0));
    }

    @Override
    public SecreHandReceipt enterBatch(String batchNo, List<SecreHandIn> rows) {
        // 整批怎么进门，只认这一处的答案
        return secreHandGate.enter(batchNo, rows);
    }

    @Override
    public SecreHandReceipt receiptOf(String batchNo) {
        // 册首三数从哪里轧出，只认这一处的答案
        return secreHandGate.receiptOf(batchNo);
    }

    @Override
    public SecreHandReceipt verify(String batchNo) {
        return secreHandGate.verify(batchNo);
    }

    @Override
    public SecreHandVerdict decideRow(String batchNo, Integer rowNo, int decision, String reason) {
        // 单行怎么定去向，只认这一处的答案
        return secreHandGate.decideRow(batchNo, rowNo, decision, reason);
    }

    @Override
    public int cancelBatch(String batchNo) {
        return secreHandGate.cancelBatch(batchNo);
    }

    @Override
    public int sinkStale(LocalDate today) {
        // 旧档那一层的沉取也归这一处判
        return secreHandGate.sinkStale(today);
    }

    @Override
    public List<TSecreHandRow> listCurrent(String batchNo) {
        return secreHandGate.listCurrent(batchNo);
    }

    @Override
    public List<TSecreHandRow> listArchive(String batchNo) {
        return secreHandGate.listArchive(batchNo);
    }
}
