package com.vcampus.client.view.main;

import com.vcampus.client.controller.AcademicController;
import com.vcampus.client.view.academic.CourseManagePanel;
import com.vcampus.client.view.academic.CourseSelectPanel;
import com.vcampus.client.view.academic.GradePanel;
import com.vcampus.common.vo.UserVO;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/**
 * 根据登录角色动态装配主界面。
 */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public MainFrame(UserVO user, AcademicController controller) {
        super("vCampusSEU - " + user.getRole().getLabel());

        JTabbedPane tabs = new JTabbedPane();
        // 根据登录角色动态装配功能模块，避免越权访问
        switch (user.getRole()) {
            case STUDENT:
                tabs.addTab("选课中心", new CourseSelectPanel(controller));
                tabs.addTab("成绩查询", new GradePanel(controller));
                break;
            case TEACHER:
            case ACADEMIC_AFFAIRS_TEACHER:
                tabs.addTab("课程管理", new CourseManagePanel(controller));
                tabs.addTab("成绩管理", new GradePanel(controller));
                break;
            case LIBRARIAN:
                tabs.addTab("图书管理", createPlaceholder("图书管理模块待扩展"));
                break;
            case STORE_MANAGER:
                tabs.addTab("商店管理", createPlaceholder("商店管理模块待扩展"));
                break;
            default:
                tabs.addTab("教务管理", new CourseManagePanel(controller));
        }

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(tabs);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    /**
     * 为尚未实现的模块提供统一占位面板。
     */
    private JPanel createPlaceholder(String text) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel(text, JLabel.CENTER), BorderLayout.CENTER);
        return panel;
    }
}
