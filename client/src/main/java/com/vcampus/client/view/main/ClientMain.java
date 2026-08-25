package com.vcampus.client.view.main;

import com.vcampus.client.controller.AcademicController;
import com.vcampus.client.controller.AuthController;
import com.vcampus.client.net.SocketClient;
import com.vcampus.client.view.login.LoginFrame;
import com.vcampus.common.vo.UserVO;

import javax.swing.SwingUtilities;

/**
 * 客户端启动入口。
 */
public class ClientMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // 所有角色共用同一个 Socket 客户端连接
                SocketClient socketClient = new SocketClient();
                AuthController authController = new AuthController(socketClient);

                // 先完成登录，未登录成功则退出客户端
                LoginFrame loginFrame = new LoginFrame(null, authController);
                loginFrame.setVisible(true);
                UserVO user = loginFrame.getLoggedUser();
                if (user == null) {
                    System.exit(0);
                    return;
                }

                // 登录成功后把当前账号绑定到教务控制器
                AcademicController academicController = new AcademicController(socketClient);
                academicController.setUid(user.getAccountNumber());
                new MainFrame(user, academicController).setVisible(true);
            }
        });
    }
}
