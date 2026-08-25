package com.vcampus.client.view.academic;

import com.vcampus.client.controller.AcademicController;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

/**
 * 课程目录管理面板（管理员/教师端）。
 */
public class CourseManagePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * 构建课程管理面板，当前阶段先搭好查询区与结果表格骨架。
     */
    public CourseManagePanel(AcademicController controller) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        searchPanel.add(new JLabel("课程关键字："));
        JTextField keywordField = new JTextField(20);
        searchPanel.add(keywordField);
        JButton queryButton = new JButton("查询课程");
        searchPanel.add(queryButton);
        add(searchPanel, BorderLayout.NORTH);

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}
