package com.vcampus.server.service.impl;

import com.vcampus.common.message.ResponseCode;
import com.vcampus.common.util.DateUtil;
import com.vcampus.common.vo.ChatMessageVO;
import com.vcampus.common.vo.UserVO;
import com.vcampus.server.dao.IChatMessageDao;
import com.vcampus.server.dao.UserDao;
import com.vcampus.server.dao.impl.ChatMessageDaoImpl;
import com.vcampus.server.dao.impl.UserDaoImpl;
import com.vcampus.server.service.IChatService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 二手商品买卖双方聊天业务实现。
 *
 * @author vCampus Team
 */
public class ChatServiceImpl implements IChatService {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final IChatMessageDao chatDao = new ChatMessageDaoImpl();
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public ResponseCode send(String fromUid, Integer itemId, String toUid, String content) {
        if (fromUid == null || itemId == null || toUid == null || content == null) {
            return ResponseCode.INVALID_REQUEST;
        }
        String from = fromUid.trim();
        String to = toUid.trim();
        String text = content.trim();
        if (from.isEmpty() || to.isEmpty() || text.isEmpty()) {
            return ResponseCode.INVALID_REQUEST;
        }
        if (from.equals(to)) {
            return ResponseCode.INVALID_REQUEST;
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            text = text.substring(0, MAX_CONTENT_LENGTH);
        }

        String fromName = from;
        UserVO sender = userDao.queryByUid(from);
        if (sender != null && sender.getName() != null && !sender.getName().isEmpty()) {
            fromName = sender.getName();
        }

        ChatMessageVO vo = new ChatMessageVO();
        vo.setItemId(itemId);
        vo.setFromUid(from);
        vo.setFromName(fromName);
        vo.setToUid(to);
        vo.setContent(text);
        vo.setSendTime(DateUtil.format(new Date()));

        try {
            return chatDao.insert(vo) ? ResponseCode.SUCCESS : ResponseCode.FAIL;
        } catch (SQLException e) {
            e.printStackTrace();
            return ResponseCode.FAIL;
        }
    }

    @Override
    public List<ChatMessageVO> history(Integer itemId, String uidA, String uidB) {
        if (itemId == null || uidA == null || uidB == null) {
            return new ArrayList<ChatMessageVO>();
        }
        try {
            return chatDao.listBetween(itemId, uidA.trim(), uidB.trim());
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<ChatMessageVO>();
        }
    }

    @Override
    public List<ChatMessageVO> conversations(Integer itemId, String sellerUid) {
        if (itemId == null || sellerUid == null) {
            return new ArrayList<ChatMessageVO>();
        }
        try {
            List<ChatMessageVO> all = chatDao.listByItem(itemId);
            Map<String, ChatMessageVO> lastByOther = new LinkedHashMap<String, ChatMessageVO>();
            Map<String, String> nameByOther = new LinkedHashMap<String, String>();
            for (ChatMessageVO m : all) {
                String other;
                if (sellerUid.equals(m.getFromUid())) {
                    other = m.getToUid();
                } else if (sellerUid.equals(m.getToUid())) {
                    other = m.getFromUid();
                    nameByOther.put(other, m.getFromName());
                } else {
                    continue;
                }
                lastByOther.put(other, m);
            }
            List<ChatMessageVO> result = new ArrayList<ChatMessageVO>();
            for (Map.Entry<String, ChatMessageVO> e : lastByOther.entrySet()) {
                String other = e.getKey();
                ChatMessageVO last = e.getValue();
                ChatMessageVO summary = new ChatMessageVO();
                summary.setItemId(itemId);
                summary.setFromUid(other);
                summary.setFromName(nameByOther.containsKey(other) ? nameByOther.get(other) : other);
                summary.setToUid(sellerUid);
                summary.setContent(last.getContent());
                summary.setSendTime(last.getSendTime());
                result.add(summary);
            }
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<ChatMessageVO>();
        }
    }
}
