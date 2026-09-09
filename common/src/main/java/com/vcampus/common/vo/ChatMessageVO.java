package com.vcampus.common.vo;

import java.io.Serializable;

/**
 * 二手商品买卖双方聊天消息值对象。
 *
 * <p>会话以「商品 + 双方」为维度：买家就某件二手商品联系卖家，
 * 卖家也可以就同一商品回复该买家。发送方与接收方由 from_uid / to_uid 标识。</p>
 *
 * @author vCampus Team
 */
public class ChatMessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息 ID */
    private Integer id;
    /** 关联的二手商品 ID */
    private Integer itemId;
    /** 发送者一卡通号 */
    private String fromUid;
    /** 发送者姓名快照 */
    private String fromName;
    /** 接收者一卡通号 */
    private String toUid;
    /** 消息内容 */
    private String content;
    /** 发送时间 (yyyy-MM-dd HH:mm:ss) */
    private String sendTime;

    public ChatMessageVO() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getItemId() { return itemId; }
    public void setItemId(Integer itemId) { this.itemId = itemId; }
    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
    public String getToUid() { return toUid; }
    public void setToUid(String toUid) { this.toUid = toUid; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSendTime() { return sendTime; }
    public void setSendTime(String sendTime) { this.sendTime = sendTime; }
}
