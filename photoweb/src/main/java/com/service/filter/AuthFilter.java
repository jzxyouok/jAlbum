package com.service.filter;

import java.io.IOException;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.backend.dao.GlobalConfDao;
import com.utils.conf.AppConfig;
import com.utils.sys.SystemConstant;
import com.utils.sys.SystemProperties;
import com.utils.sys.UUIDGenerator;

public class AuthFilter extends AbstractFilter
{
    private static final Logger logger = LoggerFactory.getLogger(AuthFilter.class);

    private static final String ORIGNAL_URI_KEY = "origuri";

    private static final String cookieNamePrefix = "SESSION";

    private static String cookieName = cookieNamePrefix;

    @Override
    public void init(FilterConfig arg0) throws ServletException
    {
        String id = GlobalConfDao.getInstance().getConf(SystemConstant.INSTANCE_ID);
        if (StringUtils.isBlank(id))
        {
            id = UUIDGenerator.getUUID();
            GlobalConfDao.getInstance().setConf(SystemConstant.INSTANCE_ID, id);
        }
        cookieName = cookieNamePrefix + "_" + id;
    }

    @Override
    protected boolean doFilterInner(HttpServletRequest httpreq, HttpServletResponse httpres)
    {
        if (!AppConfig.getInstance().needAccessAuth())
        {
            logger.info("no need to auth access!");
            return true;
        }

        if (StringUtils.equals(httpreq.getRemoteAddr(), "127.0.0.1"))
        {
            logger.info("login from local host.");
            SystemProperties.add(SystemConstant.USER_LOGIN_STATUS, LoginStatus.LocalLoin);
            return true;
        }

        String uri = httpreq.getRequestURI();

        if (StringUtils.equals("/favicon.ico", uri))
        {
            return true;
        }

        Cookie[] cookies = httpreq.getCookies();
        LoginStatus loginStatus = LoginStatus.Unlogin;
        String origUri = httpreq.getParameter(ORIGNAL_URI_KEY);
        String redirectLocation = "";
        String token = httpreq.getParameter("token");

        switch (uri)
        {
        case "/logon":
            // 登录成功，则设置cookies，并返回原入口页。
            if (TokenCache.getInstance().isSupper(token))
            {
                loginStatus = LoginStatus.SuperLogin;
                redirectLocation = (origUri == null ? "/" : origUri);
            }
            else if (TokenCache.getInstance().contains(token))
            {
                loginStatus = LoginStatus.TokenLoin;
                redirectLocation = (origUri == null ? "/" : origUri);
            }
            else
            {
                logger.warn("token error: " + token);
                loginStatus = LoginStatus.TokenError;
                redirectLocation = "/login" + (StringUtils.isBlank(origUri) ? ""
                        : "?" + ORIGNAL_URI_KEY + "=" + origUri);
            }
            break;

        case "/login":
            loginStatus = LoginStatus.WaitLogin;
            break;

        default:
            if (cookies == null || cookies.length == 0)
            {
                loginStatus = LoginStatus.Unlogin;
                redirectLocation = "/login" + "?" + ORIGNAL_URI_KEY + "=" + httpreq.getRequestURI();
            }
            else
            {
                // 正确登录，则跳转主页，并刷新过期时间。
                // 登录信息过期，或者cookies不对，则删除cookies，并跳转到登录页面。
                for (Cookie c : cookies)
                {
                    if (StringUtils.equalsIgnoreCase(cookieName, c.getName()))
                    {
                        token = c.getValue();
                        if (TokenCache.getInstance().isSupper(token))
                        {
                            loginStatus = LoginStatus.SuperLogin;
                            break;
                        }

                        if (TokenCache.getInstance().contains(token))
                        {
                            loginStatus = LoginStatus.CookiesLoin;
                            break;
                        }
                    }
                }

                if (loginStatus.equals(LoginStatus.Unlogin))
                {
                    logger.warn("cookies login error: " + token);
                    loginStatus = LoginStatus.CookiesError;
                    redirectLocation = "/login" + "?" + ORIGNAL_URI_KEY + "="
                            + httpreq.getRequestURI();
                }
            }
        }

        SystemProperties.add(SystemConstant.USER_LOGIN_STATUS, loginStatus);
        switch (loginStatus)
        {
        case WaitLogin:
            displayLogin(httpres, httpreq);
            break;
        case SuperLogin:
        case CookiesLoin:
        case TokenLoin:
            // 登录成功跳转到主页
            Cookie c = new Cookie(cookieName, token);
            c.setMaxAge(3600 * 24 * 30);
            httpres.addCookie(c);
            break;

        case CookiesError:
            if (cookies != null)
            {
                for (Cookie ctmp : cookies)
                {
                    if (StringUtils.equalsIgnoreCase(ctmp.getName(), cookieName))
                    {
                        ctmp.setMaxAge(0);
                        httpres.addCookie(ctmp);
                    }
                }
            }
        case TokenError:
        case Unlogin:
            break;

        default:
            break;
        }

        if (StringUtils.isNotBlank(redirectLocation))
        {
            goToUrl(httpres, redirectLocation);
            return false;
        }
        else
        {
            return true;
        }
    }

    private void displayLogin(HttpServletResponse httpres, HttpServletRequest httpreq)
    {
        httpres.setStatus(200);
        try
        {
            String origUri = httpreq.getParameter(ORIGNAL_URI_KEY);
            String hh = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
                    + "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"
                    + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"zh-CN\">"
                    + "<head profile=\"http://gmpg.org/xfn/11\">"
                    + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>"
                    + "<title>Login jAlbum</title></head><body style=\"text-align: center;\">"
                    + "<br/><input id=\"txt\" size=\"10\" maxlength=\"64\"/> "
                    + "<a href=\"#\" id=\"login\"><input type=\"button\" onclick=\"chref()\" "
                    + "value=\"Login\"/></a><script>function chref(){var content = "
                    + "document.getElementById(\"txt\").value;"
                    + "window.location.replace('logon?token='+content"
                    + (StringUtils.isBlank(origUri) ? "" : "+'&origuri=" + origUri + "'")
                    + ");}</script>" + "</body></html>";
            httpres.setHeader("Content-type", "text/html;charset=UTF-8");
            httpres.getWriter().write(hh);
            httpres.getWriter().close();
        }
        catch (IOException e)
        {
            logger.warn("error occured: ", e);
        }

    }

    private void goToUrl(HttpServletResponse res, String location)
    {
        res.setHeader("Location", location);
        res.setStatus(307);
    }

}
