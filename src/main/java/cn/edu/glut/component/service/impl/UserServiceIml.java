package cn.edu.glut.component.service.impl;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;

import cn.edu.glut.component.dao.UserDao;
import cn.edu.glut.component.service.UserService;
import cn.edu.glut.model.UserGrant;
import cn.edu.glut.model.UserInfo;
import cn.edu.glut.util.SendSMSCode;
//           ↓ bean id
@Service("userService")
public class UserServiceIml implements UserService{

	@Resource
	UserDao userDao;
	
	@Override
	public boolean smsCode(String tel, String checkCode) {
		try {
			SendSmsResponse sendSms = SendSMSCode.sendSms(tel, checkCode);
			if(sendSms!=null&&sendSms.getCode().equals("OK")) {
				return true;
			}
		} catch (ClientException e) {
			
			//***完善日志功能后加入日志**********
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public UserInfo regist(UserGrant userGrant) {
		//调用dao层保存,先保存userinfo,再保存userGrant
		userDao.addUserInfo(userGrant.getUser());
		userDao.addUserGrant(userGrant);
		
		
		
		return userGrant.getUser();
	}

	
	
}