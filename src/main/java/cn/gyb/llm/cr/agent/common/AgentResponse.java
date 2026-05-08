package cn.gyb.llm.cr.agent.common;

import com.alibaba.fastjson2.JSONObject;

/**
 * Agent 响应工具类
 * <p>
 * 用于构建统一格式的 Agent 响应数据，支持文本、思考过程、错误信息和 JSON 数据等类型的响应。
 * 所有方法均为静态方法，通过私有的构造函数防止实例化。
 */
public class AgentResponse {

    /**
     * 私有构造函数，防止实例化
     */
    private AgentResponse() {
    }

    /**
     * 构建文本类型的响应
     *
     * @param content 文本内容
     * @return JSON 格式的响应字符串
     */
    public static String text(String content) {
        JSONObject json = new JSONObject();
        json.put("type", "text");
        json.put("content", content);
        return json.toJSONString();
    }

    /**
     * 构建思考过程类型的响应
     *
     * @param content 思考过程内容
     * @return JSON 格式的响应字符串
     */
    public static String thinking(String content) {
        JSONObject json = new JSONObject();
        json.put("type", "thinking");
        json.put("content", content);
        return json.toJSONString();
    }

    /**
     * 构建错误类型的响应
     *
     * @param content 错误信息内容
     * @return JSON 格式的响应字符串
     */
    public static String error(String content) {
        JSONObject json = new JSONObject();
        json.put("type", "error");
        json.put("content", content);
        return json.toJSONString();
    }

    /**
     * 构建 JSON 数据类型的响应
     *
     * @param data 要返回的数据对象
     * @return JSON 格式的响应字符串
     */
    public static String json(Object data) {
        JSONObject json = new JSONObject();
        json.put("type", "json");
        json.put("content", data);
        return json.toJSONString();
    }
}
