package lab.s2jh.module.sys.service.test;

import lab.s2jh.core.test.SpringTransactionalTestCase;
import lab.s2jh.module.auth.entity.User;
import lab.s2jh.module.sys.service.UserMessageService;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UserMessageServiceTest extends SpringTransactionalTestCase {

    @Autowired
    private UserMessageService userMessageService;

    @Test
    public void findSiteCountToRead() {
        User user = new User();
        user.setId(1L);
        Long count = userMessageService.findCountToRead(user);
        logger.debug("findSiteCountToRead Count: {}", count);
    }

}
