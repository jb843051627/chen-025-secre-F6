package com.fc.v2.controller.admin;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.secre.SecreHandReceipt;
import com.fc.v2.model.auto.TSecreHandRow;
import com.fc.v2.service.ITSecreHandRowService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 清退移交行 Controller（市保密办门口核收这一处）。
 *
 * <p>一拨一册、一册一件占一行。一册一次递齐、整册进门（{@link #submit}），不接
 * 逐行零着递；同一批次码第二遍只照面不翻账；要改先走 {@link #revoke} 撤批。
 * 回执（{@link #receipt}）只复述回库逐行数出来的三数，屏上不算另一本账。
 * 现行名单与旧档那一层分口看（{@link #list}）。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "清退移交核收")
@Controller
@RequestMapping("/secreHandRow")
public class SecreHandRowController extends BaseController {

    private final String prefix = "admin/secreHandRow";

    @Autowired
    private ITSecreHandRowService secreHandRowService;

    @ApiOperation(value = "核收台账跳转", notes = "核收台账跳转")
    @GetMapping("/view")
    @RequiresPermissions("secre:secreHandRow:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "清退移交整册进门", action = "submit")
    @ApiOperation(value = "整册进门", notes = "一册一次递齐；逐行收下/退回；重批只照面")
    @PostMapping("/submit")
    @RequiresPermissions("secre:secreHandRow:submit")
    @ResponseBody
    public AjaxResult submit(String batchNo, @RequestBody List<TSecreHandRow> rows) {
        SecreHandReceipt receipt = secreHandRowService.submitBatch(batchNo, rows);
        if (receipt.isWholeReturned()) {
            // 越过单册上限：整册原样退走，库里不留半行
            AjaxResult error = AjaxResult.error(receipt.getWholeReason());
            error.put("receipt", receipt);
            return error;
        }
        AjaxResult ok = AjaxResult.success(receipt.isReceived() ? "核收完成，已出收讫" : "核收完成，空批");
        ok.put("data", receipt);
        return ok;
    }

    @Log(title = "清退移交撤批", action = "revoke")
    @ApiOperation(value = "撤批", notes = "撤干净才谈得上同批次码重递")
    @PostMapping("/revoke")
    @RequiresPermissions("secre:secreHandRow:revoke")
    @ResponseBody
    public AjaxResult revoke(String batchNo) {
        return toAjax(secreHandRowService.revokeBatch(batchNo));
    }

    @ApiOperation(value = "回执", notes = "三数回库逐行数出来；空批不出收讫；退回行带原报行次")
    @GetMapping("/receipt")
    @RequiresPermissions("secre:secreHandRow:view")
    @ResponseBody
    public AjaxResult receipt(String batchNo) {
        AjaxResult ok = AjaxResult.success();
        ok.put("data", secreHandRowService.receipt(batchNo));
        return ok;
    }

    @Log(title = "清退移交台账查询", action = "list")
    @ApiOperation(value = "台账查询", notes = "layer=current 现行名单（默认）／archive 旧档层／all 全部")
    @GetMapping("/list")
    @RequiresPermissions("secre:secreHandRow:list")
    @ResponseBody
    public ResultTable list(String batchNo, String layer) {
        startPage();
        List<TSecreHandRow> rows;
        if ("archive".equals(layer)) {
            rows = secreHandRowService.listArchiveRows(batchNo);
        } else if ("all".equals(layer)) {
            rows = secreHandRowService.listBatchRows(batchNo);
        } else {
            rows = secreHandRowService.listCurrentRows(batchNo);
        }
        com.github.pagehelper.PageInfo<TSecreHandRow> page =
                new com.github.pagehelper.PageInfo<TSecreHandRow>(rows);
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "旧档沉取", action = "sink")
    @ApiOperation(value = "沉旧档", notes = "空悬满两个年度的现行行沉入旧档层；只朝旧册开")
    @PostMapping("/sink")
    @RequiresPermissions("secre:secreHandRow:sink")
    @ResponseBody
    public AjaxResult sink(Integer refYear) {
        int year = refYear == null ? java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) : refYear;
        return toAjax(secreHandRowService.sinkExpiredRows(year));
    }
}
