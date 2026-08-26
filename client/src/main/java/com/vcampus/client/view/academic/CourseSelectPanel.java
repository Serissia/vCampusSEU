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
 * 学生选退课面板。
 *
 * @author xingyi852
 */
public class CourseSelectPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * 构建选退课面板，保留课程代码输入区和操作按钮。
     */
    public CourseSelectPanel(AcademicController controller) {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("课程代码："));
        JTextField courseCodeField = new JTextField(12);
        topPanel.add(courseCodeField);
        JButton selectButton = new JButton("选课");
        JButton dropButton = new JButton("退课");
        topPanel.add(selectButton);
        topPanel.add(dropButton);
        add(topPanel, BorderLayout.NORTH);

        JTable table = new JTable();
        add(new JScrollPane(table), BorderLayout.CENTER);
    }
}
