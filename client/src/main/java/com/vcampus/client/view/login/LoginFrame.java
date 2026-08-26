package com.vcampus.client.view.login;

import com.vcampus.client.controller.AuthController;
import com.vcampus.common.vo.UserVO;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 统一登录窗口。
 *
 * @author vCampus Team
 */
public class LoginFrame extends JDialog {

    private static final long serialVersionUID = 1L;

    private final AuthController authController;
    private final JTextField accountField = new JTextField(16);
    private final JPasswordField passwordField = new JPasswordField(16);

    private UserVO loggedUser;

    public LoginFrame(Frame owner, AuthController authController) {
        super(owner, "vCampusSEU 登录", true);
        this.authController = authController;
        initUi();
    }

    /**
     * 登录成功后返回当前用户，未登录则返回 null。
     */
    public UserVO getLoggedUser() {
        return loggedUser;
    }

    /**
     * 初始化账号、密码输入区和按钮事件。
     */
    private void initUi() {
        setLayout(new BorderLayout(8, 8));

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("账号："));
        form.add(accountField);
        form.add(new JLabel("密码："));
        form.add(passwordField);
        add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loginButton = new JButton("登录");
        JButton cancelButton = new JButton("取消");
        // 登录按钮触发鉴权，取消按钮直接关闭窗口
        loginButton.addActionListener(new LoginActionListener());
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loggedUser = null;
                dispose();
            }
        });
        buttons.add(loginButton);
        buttons.add(cancelButton);
        add(buttons, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    private class LoginActionListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            // 密码框使用 char[] 读取，避免直接暴露 String
            String account = accountField.getText().trim();
            String password = new String(passwordField.getPassword());
            if (account.length() == 0 || password.length() == 0) {
                JOptionPane.showMessageDialog(LoginFrame.this, "请输入账号和密码");
                return;
            }
            // 调用服务端完成账号密码校验
            loggedUser = authController.login(account, password);
            if (loggedUser == null) {
                JOptionPane.showMessageDialog(LoginFrame.this, "账号或密码错误");
                return;
            }
            dispose();
        }
    }
}
