package lab.s2jh.aud.entity;

import java.util.Date;
import java.util.List;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import lab.s2jh.core.annotation.MetaData;
import lab.s2jh.core.entity.PersistableEntity;
import lab.s2jh.core.web.json.DateTimeJsonSerializer;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.GenericGenerator;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** 
 * 基于logback的DBAppender表结构规范对应的实体定义
 * @see http://logback.qos.ch/manual/configuration.html#DBAppender
 */
@Getter
@Setter
@Accessors(chain = true)
@Access(AccessType.FIELD)
@Entity
@Table(name = "logging_event")
@Cache(usage = CacheConcurrencyStrategy.NONE)
@MetaData(value = "日志事件", comments = "用于基于Logback日志DBAppender的ERROR日志数据存取")
public class LoggingEvent extends PersistableEntity<Long> {

    private static final long serialVersionUID = 3807617732053699145L;

    @Id
    @Column(name = "event_id")
    @GeneratedValue(generator = "idGenerator")
    @GenericGenerator(name = "idGenerator", strategy = "native")
    private Long id;

    private Long timestmp;

    @Column(name = "formatted_message", length = 4000)
    private String formattedMessage;

    @Column(name = "logger_name", length = 256)
    private String loggerName;

    @Column(name = "level_string", length = 256)
    private String levelString;

    @Column(name = "thread_name", length = 256)
    private String threadName;

    @Column(name = "reference_flag", length = 256)
    private Integer referenceFlag;

    private String arg0;
    private String arg1;
    private String arg2;
    private String arg3;

    @Column(name = "caller_filename", length = 256)
    private String callerFilename;

    @Column(name = "caller_class", length = 256)
    private String callerClass;

    @Column(name = "caller_method", length = 256)
    private String callerMethod;

    @Column(name = "caller_line", length = 256)
    private String callerLine;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "loggingEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<LoggingEventProperty> loggingEventProperties;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "loggingEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<LoggingEventException> loggingEventExceptions;

    @Enumerated(EnumType.STRING)
    @Column(length = 32, nullable = true)
    private LoggingHandleStateEnum state = LoggingHandleStateEnum.TODO;

    @Column(length = 4000)
    @JsonIgnore
    private String operationExplain;

    public static enum LoggingHandleStateEnum {

        @MetaData("已修正")
        FIXED,

        @MetaData("待处理")
        TODO,

        @MetaData("已忽略")
        IGNORE;
    }

    @Transient
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public Date getTimestampDate() {
        return new Date(this.getTimestmp());
    }

    @Transient
    @JsonIgnore
    public String getExceptionStack() {
        StringBuilder sb = new StringBuilder();
        if (!CollectionUtils.isEmpty(loggingEventExceptions)) {
            for (LoggingEventException loggingEventException : loggingEventExceptions) {
                sb.append(loggingEventException.getTraceLine() + "\n");
            }
        }
        return sb.toString();
    }

    @Override
    @Transient
    public String getDisplay() {
        return StringUtils.substring(formattedMessage, 0, 50);
    }

}
