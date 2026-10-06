package com.fc.v2.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.common.secre.SecreSyncReport;
import com.fc.v2.model.auto.TSecreOrgBook;
import com.fc.v2.service.ITSecreOrgBookService;
import com.fc.v2.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 机构名录底册 Controller
 *
 * <p>本机关自录走 add；上级名录每天下发一份走 sync——同一个代号两头对不上时，
 * 以本机关这份作准，冲突逐条点名到代号，缘由落到本地名录那一份的备注里。</p>
 *
 * @author fuce
 * @date 2026-10-06
 */
@Api(value = "机构名录底册")
@Controller
@RequestMapping("/SecreOrgBookController")
public class SecreOrgBookController extends BaseController {

    private final String prefix = "admin/secreOrgBook";

    @Autowired
    private ITSecreOrgBookService secreOrgBookService;

    @ApiOperation(value = "分页跳转", notes = "分页跳转")
    @GetMapping("/view")
    @RequiresPermissions("secre:secreOrgBook:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "机构名录底册集合查询", action = "list")
    @ApiOperation(value = "分页查询", notes = "分页查询")
    @GetMapping("/list")
    @RequiresPermissions("secre:secreOrgBook:list")
    @ResponseBody
    public ResultTable list(TSecreOrgBook record) {
        QueryWrapper<TSecreOrgBook> queryWrapper = new QueryWrapper<TSecreOrgBook>();
        queryWrapper.eq("del_flag", 0);
        if (record != null) {
            if (StringUtils.isNotEmpty(record.getBookNo())) {
                queryWrapper.eq("book_no", record.getBookNo());
            }
            if (StringUtils.isNotEmpty(record.getBookName())) {
                queryWrapper.like("book_name", record.getBookName());
            }
            if (StringUtils.isNotEmpty(record.getRoadName())) {
                queryWrapper.eq("road_name", record.getRoadName());
            }
            if (StringUtils.isNotEmpty(record.getSyncKind())) {
                queryWrapper.eq("sync_kind", record.getSyncKind());
            }
            if (record.getStatus() != null) {
                queryWrapper.eq("status", record.getStatus());
            }
        }
        startPage();
        com.github.pagehelper.PageInfo<TSecreOrgBook> page =
                new com.github.pagehelper.PageInfo<TSecreOrgBook>(secreOrgBookService.selectTSecreOrgBookList(queryWrapper));
        return pageTable(page.getList(), page.getTotal());
    }

    @Log(title = "机构名录底册新增", action = "add")
    @ApiOperation(value = "本机关自录", notes = "本机关自录一册，来路记本机关自录")
    @PostMapping("/add")
    @RequiresPermissions("secre:secreOrgBook:add")
    @ResponseBody
    public AjaxResult add(TSecreOrgBook record) {
        return toAjax(secreOrgBookService.insertTSecreOrgBook(record));
    }

    @Log(title = "机构名录底册修改", action = "edit")
    @ApiOperation(value = "修改保存", notes = "修改保存（代号不动；记已撤出只挡新行，不抹旧账）")
    @PostMapping("/edit")
    @RequiresPermissions("secre:secreOrgBook:edit")
    @ResponseBody
    public AjaxResult editSave(TSecreOrgBook record) {
        return toAjax(secreOrgBookService.updateTSecreOrgBook(record));
    }

    @Log(title = "机构名录底册删除", action = "remove")
    @ApiOperation(value = "删除", notes = "删除")
    @DeleteMapping("/remove")
    @RequiresPermissions("secre:secreOrgBook:remove")
    @ResponseBody
    public AjaxResult remove(String ids) {
        return toAjax(secreOrgBookService.deleteTSecreOrgBookByIds(ids));
    }

    @Log(title = "上级名录下发合并", action = "sync")
    @ApiOperation(value = "上级名录下发", notes = "对不上以本机关这份作准；冲突逐条点名到代号并落备注")
    @PostMapping("/sync")
    @RequiresPermissions("secre:secreOrgBook:sync")
    @ResponseBody
    public AjaxResult sync(@RequestBody List<TSecreOrgBook> superiorBooks) {
        SecreSyncReport report = secreOrgBookService.syncSuperiorBooks(superiorBooks);
        AjaxResult ok = AjaxResult.success("名录下发合并完成");
        ok.put("data", report);
        return ok;
    }
}
