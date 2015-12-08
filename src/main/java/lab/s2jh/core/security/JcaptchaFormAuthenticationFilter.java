package lab.s2jh.core.security;

import java.util.Date;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lab.s2jh.aud.entity.UserLogonLog;
import lab.s2jh.core.security.SourceUsernamePasswordToken.AuthSourceEnum;
import lab.s2jh.core.util.DateUtils;
import lab.s2jh.core.util.IPAddrFetcher;
import lab.s2jh.core.util.UidUtils;
import lab.s2jh.core.web.captcha.ImageCaptchaServlet;
import lab.s2jh.module.auth.entity.User;
import lab.s2jh.module.auth.entity.UserExt;
import lab.s2jh.module.auth.service.UserService;

import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.authc.FormAuthenticationFilter;
import org.apache.shiro.web.util.WebUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

public class JcaptchaFormAuthenticationFilter extends FormAuthenticationFilter {

    private static final Logger logger = LoggerFactory.getLogger(JcaptchaFormAuthenticationFilter.class);

    public static final Integer LOGON_FAILURE_LIMIT = 2;

    /**
    * 达到验证失败次数限制，传递标志属性，登录界面显示验证码输入
    */
    public static final String KEY_AUTH_CAPTCHA_REQUIRED = "auth_captcha_required";

    /**
    * 记录用户输入的用户名信息，用于登录界面回显
    */
    public static final String KEY_AUTH_USERNAME_VALUE = "auth_username_value";

    /**
    * 默认验证码参数名称
    */
    public static final String DEFAULT_VALIDATE_CODE_PARAM = "captcha";

    //验证码参数名称
    private String captchaParam = DEFAULT_VALIDATE_CODE_PARAM;

    private UserService userService;

    /**
     * 是否强制转向指定successUrl，忽略登录之前自动保存的URL
     */
    private boolean forceSuccessUrl = false;

    private boolean isMobileAppAccess(ServletRequest request) {
        //获取设备ID标识
        String uuid = request.getParameter("uuid");
        return StringUtils.isNotBlank(uuid);
    }

    protected AuthenticationToken createToken(String username, String password, ServletRequest request, ServletResponse response) {
        boolean rememberMe = isRememberMe(request);
        String host = getHost(request);
        SourceUsernamePasswordToken token = new SourceUsernamePasswordToken(username, password, rememberMe, host);
        String source = request.getParameter("source");
        //获取设备ID标识
        String uuid = request.getParameter("uuid");
        token.setUuid(uuid);
        if (StringUtils.isNotBlank(source)) {
            token.setSource(Enum.valueOf(AuthSourceEnum.class, source));
        } else {
            if (isMobileAppAccess(request)) {
                token.setSource(AuthSourceEnum.P);
            }
        }
        return token;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        if (isLoginRequest(request, response)) {
            if (isLoginSubmission(request, response)) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Login submission detected.  Attempting to execute login.");
                }
                return executeLogin(request, response);
            } else {
                if (logger.isTraceEnabled()) {
                    logger.trace("Login page view.");
                }
                //allow them to see the login page ;)
                return true;
            }
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Attempting to access a path which requires authentication.  Forwarding to the " + "Authentication url ["
                        + getLoginUrl() + "]");
            }

            if (isMobileAppAccess(request)) {
                HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                httpServletResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
                return true;
            } else {
                saveRequestAndRedirectToLogin(request, response);
                return false;
            }
        }
    }

    @Override
    protected boolean executeLogin(ServletRequest request, ServletResponse response) throws Exception {
        SourceUsernamePasswordToken token = (SourceUsernamePasswordToken) createToken(request, response);
        try {
            String username = getUsername(request);
            //写入登录账号名称用于回显
            request.setAttribute(KEY_AUTH_USERNAME_VALUE, username);

            User authAccount = userService.findByAuthTypeAndAuthUid(User.AuthTypeEnum.SYS, username);
            if (authAccount != null) {

                //失败LOGON_FAILURE_LIMIT次，强制要求验证码验证
                if (authAccount.getLogonFailureTimes() > LOGON_FAILURE_LIMIT) {
                    String captcha = request.getParameter(captchaParam);
                    if (StringUtils.isBlank(captcha) || !ImageCaptchaServlet.validateResponse((HttpServletRequest) request, captcha)) {
                        throw new CaptchaValidationException("验证码不正确");
                    }
                }

                Subject subject = getSubject(request, response);
                subject.login(token);
                return onLoginSuccess(token, subject, request, response);
            } else {
                return onLoginFailure(token, new UnknownAccountException("登录账号或密码不正确"), request, response);
            }
        } catch (AuthenticationException e) {
            return onLoginFailure(token, e, request, response);
        }
    }

    /**
    * 重写父类方法，当登录失败次数大于allowLoginNum（允许登录次）时，将显示验证码
    */
    @Override
    protected boolean onLoginFailure(AuthenticationToken token, AuthenticationException e, ServletRequest request, ServletResponse response) {
        if (e instanceof CaptchaValidationException) {
            request.setAttribute(KEY_AUTH_CAPTCHA_REQUIRED, Boolean.TRUE);
        } else if (e instanceof IncorrectCredentialsException) {
            //消息友好提示
            e = new IncorrectCredentialsException("登录账号或密码不正确");
            //失败记录
            SourceUsernamePasswordToken sourceUsernamePasswordToken = (SourceUsernamePasswordToken) token;
            User user = userService.findByAuthTypeAndAuthUid(User.AuthTypeEnum.SYS, sourceUsernamePasswordToken.getUsername());
            if (user != null) {
                UserExt userExt = user.getUserExt();
                userExt.setLogonTimes(userExt.getLogonTimes() + 1);
                userExt.setLastLogonFailureTime(DateUtils.currentDate());
                userService.saveExt(userExt);

                user.setLogonFailureTimes(user.getLogonFailureTimes() + 1);
                userService.save(user);

                //达到验证失败次数限制，传递标志属性，登录界面显示验证码输入
                if (user.getLogonFailureTimes() > LOGON_FAILURE_LIMIT) {
                    request.setAttribute(KEY_AUTH_CAPTCHA_REQUIRED, Boolean.TRUE);
                }
            }
        }
        return super.onLoginFailure(token, e, request, response);
    }

    /**
    * 重写父类方法，当登录成功后，重置失败标志
    */
    @Override
    protected boolean onLoginSuccess(AuthenticationToken token, Subject subject, ServletRequest request, ServletResponse response) throws Exception {
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        SourceUsernamePasswordToken sourceUsernamePasswordToken = (SourceUsernamePasswordToken) token;
        User authAccount = userService.findByAuthTypeAndAuthUid(User.AuthTypeEnum.SYS, sourceUsernamePasswordToken.getUsername());
        Date now = DateUtils.currentDate();

        //更新Access Token，并设置半年后过期
        if (StringUtils.isBlank(authAccount.getAccessToken()) || authAccount.getAccessTokenExpireTime().before(now)) {
            authAccount.setAccessToken(UidUtils.UID());
            authAccount.setAccessTokenExpireTime(new DateTime(DateUtils.currentDate()).plusMonths(6).toDate());
            userService.save(authAccount);
        }

        //写入登入记录信息
        UserLogonLog userLogonLog = new UserLogonLog();
        userLogonLog.setLogonTime(DateUtils.currentDate());
        userLogonLog.setLogonYearMonthDay(DateUtils.formatDate(userLogonLog.getLogoutTime()));
        userLogonLog.setRemoteAddr(httpServletRequest.getRemoteAddr());
        userLogonLog.setRemoteHost(httpServletRequest.getRemoteHost());
        userLogonLog.setRemotePort(httpServletRequest.getRemotePort());
        userLogonLog.setLocalAddr(httpServletRequest.getLocalAddr());
        userLogonLog.setLocalName(httpServletRequest.getLocalName());
        userLogonLog.setLocalPort(httpServletRequest.getLocalPort());
        userLogonLog.setServerIP(IPAddrFetcher.getGuessUniqueIP());
        userLogonLog.setHttpSessionId(httpServletRequest.getSession().getId());
        userLogonLog.setUserAgent(httpServletRequest.getHeader("User-Agent"));
        userLogonLog.setXforwardFor(IPAddrFetcher.getRemoteIpAddress(httpServletRequest));
        userLogonLog.setAuthType(authAccount.getAuthType());
        userLogonLog.setAuthUid(authAccount.getAuthUid());
        userLogonLog.setAuthGuid(authAccount.getAuthGuid());
        userService.userLogonLog(authAccount, userLogonLog);

        if (isMobileAppAccess(request)) {
            return true;
        } else {
            //根据不同登录类型转向不同成功界面
            AuthUserDetails authUserDetails = AuthContextHolder.getAuthUserDetails();

            //判断密码是否已到期，如果是则转向密码修改界面
            Date credentialsExpireTime = authAccount.getCredentialsExpireTime();
            if (credentialsExpireTime != null && credentialsExpireTime.before(DateUtils.currentDate())) {
                httpServletResponse.sendRedirect(httpServletRequest.getContextPath() + authUserDetails.getUrlPrefixBySource()
                        + "/profile/credentials-expire");
                return false;
            }

            //如果是强制转向指定successUrl则清空SavedRequest
            if (forceSuccessUrl) {
                WebUtils.getAndClearSavedRequest(httpServletRequest);
            }

            return super.onLoginSuccess(token, subject, request, httpServletResponse);
        }
    }

    protected void setFailureAttribute(ServletRequest request, AuthenticationException ae) {
        //写入认证异常对象用于错误显示
        request.setAttribute(getFailureKeyAttribute(), ae);
    }

    public void setCaptchaParam(String captchaParam) {
        this.captchaParam = captchaParam;
    }

    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    public static class CaptchaValidationException extends AuthenticationException {

        private static final long serialVersionUID = -7285314964501172092L;

        public CaptchaValidationException(String message) {
            super(message);
        }
    }

    public void setForceSuccessUrl(boolean forceSuccessUrl) {
        this.forceSuccessUrl = forceSuccessUrl;
    }

}
