package cn.gyb.llm.cr.agent.controller;

import cn.gyb.llm.cr.agent.common.ReviewResult;
import cn.gyb.llm.cr.agent.entity.db.ReviewIssueRecord;
import cn.gyb.llm.cr.agent.entity.db.ReviewTask;
import cn.gyb.llm.cr.agent.mapper.ReviewIssueRecordMapper;
import cn.gyb.llm.cr.agent.mapper.ReviewTaskMapper;
import cn.gyb.llm.cr.agent.service.ReviewService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 代码审查控制器，提供手动触发审查和任务查询接口。
 * <p>
 * 支持手动触发 MR 代码审查和全量项目扫描，以及查询审查任务列表和详情。
 * 所有触发操作为异步执行，立即返回任务状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    /** 代码审查服务 */
    @Autowired
    private ReviewService reviewService;

    /** 审查任务数据库 Mapper */
    @Autowired
    private ReviewTaskMapper reviewTaskMapper;

    /** 审查问题记录数据库 Mapper */
    @Autowired
    private ReviewIssueRecordMapper reviewIssueRecordMapper;

    /**
     * 手动触发指定合并请求的代码审查。
     *
     * @param projectId       GitLab 项目 ID
     * @param mergeRequestIid 合并请求 IID
     * @return 包含项目 ID、MR IID 和处理状态的响应
     */
    @PostMapping("/mr")
    public ResponseEntity<Map<String, Object>> reviewMergeRequest(
            @RequestParam Long projectId,
            @RequestParam Long mergeRequestIid) {

        log.info("手动 MR 审查请求: projectId={}, mergeRequestIid={}", projectId, mergeRequestIid);

        Map<String, Object> response = new HashMap<>();
        response.put("projectId", projectId);
        response.put("mergeRequestIid", mergeRequestIid);
        response.put("status", "PROCESSING");

        CompletableFuture.runAsync(() -> {
            try {
                reviewService.reviewMergeRequest(projectId, mergeRequestIid);
            } catch (Exception e) {
                log.error("异步 MR 审查失败: projectId={}, mergeRequestIid={}",
                        projectId, mergeRequestIid, e.getMessage(), e);
            }
        });

        return ResponseEntity.accepted().body(response);
    }

    /**
     * 触发全量项目代码扫描。
     *
     * @param projectId GitLab 项目 ID
     * @return 包含项目 ID 和处理状态的响应
     */
    @PostMapping("/full-scan")
    public ResponseEntity<Map<String, Object>> triggerFullScan(@RequestParam Long projectId) {
        log.info("全量扫描请求: projectId={}", projectId);

        Map<String, Object> response = new HashMap<>();
        response.put("projectId", projectId);
        response.put("status", "PROCESSING");

        CompletableFuture.runAsync(() -> {
            try {
                reviewService.triggerFullScan(projectId);
            } catch (Exception e) {
                log.error("异步全量扫描失败: projectId={}", projectId, e.getMessage(), e);
            }
        });

        return ResponseEntity.accepted().body(response);
    }

    /**
     * 列出最近的审查任务（最近 50 条，按 created_at 降序排列）。
     *
     * @return 审查任务列表
     */
    @GetMapping("/tasks")
    public ResponseEntity<List<ReviewTask>> listRecentTasks() {
        QueryWrapper<ReviewTask> query = new QueryWrapper<ReviewTask>()
                .orderByDesc("created_at")
                .last("LIMIT 50");
        List<ReviewTask> tasks = reviewTaskMapper.selectList(query);
        return ResponseEntity.ok(tasks);
    }

    /**
     * 获取任务详情及关联的问题列表。
     *
     * @param taskId 任务 ID
     * @return 包含任务信息和问题列表的详情对象
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskDetail(@PathVariable Long taskId) {
        ReviewTask task = reviewTaskMapper.selectById(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        QueryWrapper<ReviewIssueRecord> issueQuery = new QueryWrapper<ReviewIssueRecord>()
                .eq("review_task_id", taskId);
        List<ReviewIssueRecord> issues = reviewIssueRecordMapper.selectList(issueQuery);

        Map<String, Object> detail = new HashMap<>();
        detail.put("task", task);
        detail.put("issues", issues);
        return ResponseEntity.ok(detail);
    }
}
