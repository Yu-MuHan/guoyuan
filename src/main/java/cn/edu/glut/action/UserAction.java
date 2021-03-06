package cn.edu.glut.action;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import javax.annotation.Resource;
import javax.jms.MessageConsumer;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.sun.scenario.effect.impl.sw.java.JSWBlend_EXCLUSIONPeer;

import cn.edu.glut.component.service.OrderService;
import cn.edu.glut.component.service.UserService;
import cn.edu.glut.model.ReceiverAddress;
import cn.edu.glut.model.ServerResponse;
import cn.edu.glut.model.UserGrant;
import cn.edu.glut.model.UserInfo;
import cn.edu.glut.util.DebugOut;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JsonConfig;

/**
 * 
 * @author 于金彪
 *
 */
@Controller
@RequestMapping(value = "user")
public class UserAction {

	@Resource(name = "userService")
	UserService userService;
	
	@Resource(name = "orderService")
	private OrderService orderService;
	
	Logger log = LogManager.getLogger();
	Logger record = LogManager.getLogger("recordFile");

	/**
	 * 发送验证码的方法
	 * 
	 * @param tel      手机号必传
	 * @param regist   注册时必传,值为true。其他情况可省略
	 * @param response
	 * @param session
	 * @return 短信发送成功返回true 发送失败返回false。注册时失败返回错误号,1001：手机号已注册
	 * 
	 */
	@RequestMapping(value = "smsCode")
	public String smsCode(@RequestParam(name = "tel", required = true) String tel,
			@RequestParam(name = "regist", required = false) String regist, HttpServletResponse response,
			HttpSession session) {
		// 设置response 文本类型
		response.setContentType("text/html;charset=utf-8");
		try {
			// 如果是注册先判断手机号
			UserInfo user = userService.getUserByTel(tel);

			if (regist != null && regist.contains("true")) {
				if (user != null) {
					response.getWriter().println("1001");
					return null;
				}
			} else {
				// 登录没账号
				if (user == null) {
					response.getWriter().println("1002");
					return null;
				}
			}

			Random random = new Random();
			// 保证验证码6位数
			SendSmsResponse sendSms = null;
			int checkCode = random.nextInt(899999) + 100000;
			PrintWriter pw = null;
			// 验证码发送结果 成功true
			boolean flag = userService.smsCode(tel, String.valueOf(checkCode));

			pw = response.getWriter();
			if (flag) {
				session.setAttribute("tel", tel);
				session.setAttribute("checkCode", String.valueOf(checkCode));
				System.out.println(checkCode);
				pw.println("true");
			} else {

				pw.print("false");
			}
		} catch (IOException e) {
			log.error("IOException", e);
		}

		return null;
	}

	/**
	 * 
	 * 校验验证码
	 * 
	 * @param smsCode
	 * @param resp
	 * @param session
	 * @return
	 */
	@RequestMapping(value = "checkSMSCode")
	public String checkSMSCode(@RequestParam(name = "smsCode", required = true) String smsCode,
			HttpServletResponse resp, HttpSession session) {
		try {
			// 先验证是否发送了验证码
			String tel = (String) session.getAttribute("tel");
			if (tel == null) {
				resp.getWriter().println("noTelephoneNumber");

				return null;
			}
			// 校验验证码页面对验证码进行非空验证
			String checkCode = (String) session.getAttribute("checkCode");
			if (smsCode != null && smsCode.equals(checkCode)) {
				resp.getWriter().println("true");
				// 已通过手机号验证
				session.setAttribute("validTelephone", true);

				return null;
			} else {
				resp.getWriter().println("false");
				return null;
			}
		} catch (IOException e) {
			log.error("IOException", e);
		}

		return null;
	}

	/**
	 * 注册功能账号 是已验证的手机号
	 * 
	 * @param pwd     密码
	 * @param request
	 * @param session
	 * @return 返回页面路径待定
	 */
	@RequestMapping(value = "regist")
	public String regist(@RequestParam(name = "pwd", required = true) String pwd, HttpServletRequest request,
			HttpServletResponse response, HttpSession session) {
		String tel = (String) session.getAttribute("tel");
		if (tel == null) {
			// 不应该进到此处 记录日志
			record.debug("手机号未发送过验证码:" + tel);
			return null;
		}

		// 确保经过手机号验证
		Boolean checked = (Boolean) session.getAttribute("validTelephone");

		// 未经过手机号验证 正常情况不会出现 一旦出现记录日志
		if (checked == null || !checked) {
			// 记录日志
			record.debug("手机未经过验证码验证");
			return null;
		}

		UserInfo user = new UserInfo();
		UserGrant userGrant = new UserGrant();

		// 记录注册ip地址
		user.setIp(request.getRemoteAddr());

		// 调用service 层通过ip地址查出地理位置 未完成
		user.setTelephone(tel);
		// 用户状态 约定0禁止登陆 1允许登陆
		user.setState(1);

		userGrant.setLoginType("telephone");
		userGrant.setGrantCode(pwd);
		// 身份码
		userGrant.setIdentifier(tel);
		
		List<UserGrant> grants=new ArrayList<>();
		grants.add(userGrant);
		
		String openId=(String)session.getAttribute("openId");
		if(openId!=null) {
			UserGrant wxGrant=new UserGrant();
			wxGrant.setLoginType("weixin");
			wxGrant.setGrantCode(openId);
			wxGrant.setIdentifier(tel);
			grants.add(wxGrant);
		}
		user.setGrants(grants);
		// 调用service 进行注册
		user = userService.regist(user);

		try (PrintWriter pw = response.getWriter()) {

			if (user == null || user.getUserId() == null) {
				// *******出错记录日志
				log.error("用户注册失败:" + tel);
				pw.print("error");
			} else {
				// 注册成功 修改视图返回首页
				session.setAttribute("user", user);
				pw.print("true");
				return null;
			}
		} catch (IOException e) {
			log.error("程序异常", e);
		}

		return null;
	}

	/**
	 * 手机+密码的登陆方式
	 * 
	 * @param tel     手机号
	 * @param pwd     密码
	 * @param session
	 * @return 成功返回我的页面 失败提示错误信息
	 */
	@RequestMapping(value = "login")
	public String login(@RequestParam(name = "tel") String tel, @RequestParam(name = "pwd") String pwd,
			HttpSession session, HttpServletResponse response,HttpServletRequest request) {
		//服务器响应对象
		ServerResponse sr=new ServerResponse();
		
		UserInfo user = userService.getUserByTel(tel);
		if(user==null) {
			try {
				sr.setReturn_code("FAIL");
				sr.setReturn_msg("密码错误");
				response.getWriter().print(JSONObject.fromObject(sr));
				return null;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		List<UserGrant> grants = user.getGrants();

		try (PrintWriter pw = response.getWriter()) {
			for (UserGrant grant : grants) {
				if ("telephone".equals(grant.getLoginType())) {
					// 找到密码的授权方式
					if (pwd.equals(grant.getGrantCode())) {
						// 密码匹配成功
						session.setAttribute("user", user);
						//创建一个jms消费者
						MessageConsumer consumer=userService.registMessageConsumer(user);
						session.setAttribute("jmsConsumer", consumer);
						
						
						//从session中取出目标路径
						String url =(String) session.getAttribute("targetPath");
						//取出参数
						String para= (String) session.getAttribute("targetParam");
						JSONObject targetPara=JSONObject.fromObject(para);
						
						Iterator<String> keys=null;
						if(para!=null)
						keys=targetPara.keys();
						
						StringBuffer sb=new StringBuffer("?login=true");
						
						while(keys!=null&&keys.hasNext()) {
							String key=keys.next();
							System.out.println(key);
							JSONArray array=((JSONArray)targetPara.get(key));
							List values= JSONArray.toList(array,"",new JsonConfig());
							for (int i = 0; i < values.size(); i++) {
								sb.append("&"+key+"="+values.get(i));
							}
						}
						url=url+sb.toString();
						url=request.getContextPath()+url;
						DebugOut.print("登录成功重定向到目标路径 :"+url);
						sr.setReturn_msg("登录成功,重定向至目标路径");
						sr.setReturn_code("SUCCESS");
						Map data=sr.getData();
						data.put("url", url);
						sr.setData(data);
						
						pw.print(net.sf.json.JSONObject.fromObject(sr));
						return null;
					} else {
						// 密码错误
						sr.setReturn_code("FAIL");
						sr.setReturn_msg("密码错误");
						pw.print(JSONObject.fromObject(sr));
						return null;
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 短信验证码登录方式
	 * 
	 * @param tel     手机号
	 * @param SMSCode 短信验证码
	 * @return
	 */
	@RequestMapping("loginBySMS")
	public ModelAndView loginBySMS(@RequestParam(name = "tel") String tel,
			@RequestParam(name = "SMSCode") String SMSCode, HttpSession session) {
		if (tel == null || tel.trim().equals("")) {
			log.error("登陆异常，手机号码为空");
			return null;
		}
		ModelAndView result = new ModelAndView();
		if (tel.equals(session.getAttribute("tel")) && (Boolean) session.getAttribute("validTelephone")) {
			UserInfo user = userService.getUserByTel(tel);
			result.setViewName("my");
			session.setAttribute("user", user);
			MessageConsumer consumer=userService.registMessageConsumer(user);
			session.setAttribute("jmsConsumer", consumer);
		} else {
			log.error("登陆错误，未通过验证,或遭到恶意分析：+"+tel);
			result.setViewName("login");
		}

		return result;
	}

	/**
	 * 返回主页
	 * 
	 * @return
	 */
	@RequestMapping("home")
	public String home(@RequestParam("code") String code,HttpSession session) {
		//调用业务层通过code获取opeid
		String oldopenId=(String) session.getAttribute("openId");

		if(oldopenId!=null) {
			DebugOut.print("缓存的oldopenId");
			return "home";
		}
		String openId = userService.getOpenId(code);
		//调用业务层通过openId获取UserInfo
		UserInfo user=userService.getUserByOpenId(openId);
		//如果没有找到user 吧openId放到session
		if(user!=null){
			session.setAttribute("user", user);
		}
		session.setAttribute("openId", openId);
		return "home";
	}
/**
 * 
 * 添加收货地址
 * @return
 */
	@RequestMapping("addShippingAddr")
	public ModelAndView addShippingAddr(ReceiverAddress ra,HttpSession session) {
		UserInfo user= (UserInfo)session.getAttribute("user");
		ra.setUserId(user.getUserId());
		ModelAndView mv=new ModelAndView();
		List<ReceiverAddress> addrs=userService.addAddr(ra);
		DebugOut.print(ra.getReceiverAddressId());
		mv.setViewName("address");
		mv.addObject("addrs", JSONArray.fromObject(addrs).toString());
		
		
		return mv;
	}
	
	/**
	 * 查看收货地址
	 * @return
	 */
	@RequestMapping("address")
	public ModelAndView address(@RequestParam(name="forJson" ,required=false)String forJson,HttpSession session,HttpServletResponse response) {
		response.setCharacterEncoding("utf-8");
		UserInfo user = (UserInfo) session.getAttribute("user");
		List<ReceiverAddress> addrs= userService.getReceiverAddress(user.getUserId());
		//返回json数据
		JSONArray result=JSONArray.fromObject(addrs);
		if(forJson!=null&&forJson.trim().equals("true")) {
			try {
				response.getWriter().println(result);
				
			} catch (IOException e) {
				e.printStackTrace();
			}
			return null;
		}
		ModelAndView mv=new ModelAndView("address");
		mv.addObject("addrs",result.toString());
		return mv;
	}

	/**
	 * 查看收货地址 返回json 数据
	 * @param number 运单号
	 * @param exp 快递公司
	 * @return
	 */
	@RequestMapping("expressInfo")
	public ModelAndView expressInfo(@RequestParam("number")String number,@RequestParam("exp") String exp,HttpServletResponse response) {
		
		String expressInfo=userService.queryExpressInfo(number,exp);
		try {
			response.getWriter().print(expressInfo);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * 获取默认收货地址
	 * @param response
	 * @param session
	 */
	@RequestMapping("getDefaultAddress")
	public void getDefaultAddress(HttpServletResponse response,HttpSession session) {
		UserInfo user =(UserInfo)session.getAttribute("user");
		ReceiverAddress addr= orderService.getDefaultAddr(user.getUserId());
		JSONObject obj=JSONObject.fromObject(addr);
		try {
			response.getWriter().print(obj.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}
}
