package step.core.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Objects;

import static step.core.deployment.AbstractServices.SESSION;

public class AuditLogger {
    private static Logger auditLogger = LoggerFactory.getLogger("AuditLogger");
    private static ObjectMapper objectMapper = new ObjectMapper();

    public static void logResponse(HttpServletRequest req, int status) {
        if ((auditLogger.isTraceEnabled() || req.getRequestURI().equals("/rest/access/login")) &&
                !req.getRequestURI().equals("/rest/access/logout")) {
          log(req, status);  
        }
    }
    
    public static void log(HttpServletRequest req, int status) {
        String log = getLogMessage(req, status);
        if (status <=200 && status < 400){
            auditLogger.info(log);
        } else {
            auditLogger.warn(log);
        }
    }
    
    //called by session invalidation (no request context)
    public static void logSessionInvalidation(HttpSession httpSession) {
        Session session = (Session) httpSession.getAttribute(SESSION);
        String user = (session != null && session.getUser() != null) ? session.getUser().getUsername() : "unknown";
        AuditMessage msg = new AuditMessage();
        msg.req = "Session invalidation";
        msg.sesId = httpSession.getId();
        msg.user = user;
        try {
            auditLogger.info(objectMapper.writeValueAsString(msg));
        } catch (JsonProcessingException e) {
            auditLogger.info("Message could not be serialized for " + msg);
        }
    }
    
    private static String getLogMessage(HttpServletRequest req, int status)  {
        String forwardedFor = req.getHeader("X-Forwarded-For");
        String source = Objects.requireNonNullElse(forwardedFor, req.getRemoteAddr()+":"+req.getRemotePort());
        String user;
        try {
            user = ((Session) req.getSession().getAttribute(SESSION)).getUser().getUsername();
        } catch (Exception e) {
            user = "unknown";
        }
        AuditMessage msg = new AuditMessage();
        msg.req = req.getMethod() + " " + req.getRequestURI(); 
        msg.sesId = req.getSession().getId();
        msg.src = source;
        msg.user = user;
        msg.agent = req.getHeader("User-Agent");
        msg.sc = status;
        try {
            return objectMapper.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            return "Message could not be serialized for " + msg;
        }
    }
    
    public static class AuditMessage {
        String req = "-";
        String sesId = "-";
        String src = "-";
        String user = "-";
        String agent = "-";
        int sc = -1;
        
        public AuditMessage(){super();}

        public String getReq() {
            return req;
        }

        public void setReq(String req) {
            this.req = req;
        }

        public String getSesId() {
            return sesId;
        }

        public void setSesId(String sesId) {
            this.sesId = sesId;
        }

        public String getSrc() {
            return src;
        }

        public void setSrc(String src) {
            this.src = src;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public String getAgent() {
            return agent;
        }

        public void setAgent(String agent) {
            this.agent = agent;
        }

        public int getSc() {
            return sc;
        }

        public void setSc(int sc) {
            this.sc = sc;
        }

        @Override
        public String toString() {
            return "AuditMessage{" +
                    "req='" + req + '\'' +
                    ", sesId='" + sesId + '\'' +
                    ", src='" + src + '\'' +
                    ", user='" + user + '\'' +
                    ", agent='" + agent + '\'' +
                    ", sc=" + sc +
                    '}';
        }
    }




}
