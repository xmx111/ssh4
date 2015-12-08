package lab.s2jh.core.dao.mybatis;

import java.util.List;
import java.util.Map;

import lab.s2jh.core.pagination.GroupPropertyFilter;

import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.github.loafer.mybatis.pagination.PaginationInterceptor;

public class MyBatisGenerialDaoImpl implements MyBatisDao {

    private SqlSession sqlSession;

    @Override
    public <E> List<E> findList(String namespace, String statementId, Map<String, Object> parameters) {
        return findLimitList(namespace, statementId, parameters, null);
    }

    @Override
    public <E> List<E> findLimitList(String namespace, String statementId, Map<String, Object> parameters, Integer top) {
        String statement = namespace + "." + statementId;
        if (top != null) {
            RowBounds rowBounds = new RowBounds(0, top);
            return sqlSession.selectList(statement, parameters, rowBounds);
        } else {
            return sqlSession.selectList(statement, parameters);
        }
    }

    @Override
    public <E> List<E> findSortList(String namespace, String statementId, GroupPropertyFilter groupPropertyFilter, Sort sort) {
        String statement = namespace + "." + statementId;
        Map<String, Object> parameters = groupPropertyFilter.convertToMapParameters();
        if (sort != null) {
            RowBounds rowBounds = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);
            PaginationInterceptor.setPaginationOrderby(sort);
            return sqlSession.selectList(statement, parameters, rowBounds);
        } else {
            return sqlSession.selectList(statement, parameters);
        }
    }

    @Override
    public <E> List<E> findSortList(String namespace, String statementId, Map<String, Object> parameters, Sort sort) {
        String statement = namespace + "." + statementId;
        if (sort != null) {
            RowBounds rowBounds = new RowBounds(RowBounds.NO_ROW_OFFSET, RowBounds.NO_ROW_LIMIT);
            PaginationInterceptor.setPaginationOrderby(sort);
            return sqlSession.selectList(statement, parameters, rowBounds);
        } else {
            return sqlSession.selectList(statement, parameters);
        }
    }

    @Override
    public <E> Page<E> findPage(String namespace, String statementId, GroupPropertyFilter groupPropertyFilter, Pageable pageable) {
        String statement = namespace + "." + statementId;
        Map<String, Object> parameters = groupPropertyFilter.convertToMapParameters();
        RowBounds rowBounds = new RowBounds(pageable.getOffset(), pageable.getPageSize());
        PaginationInterceptor.setPaginationOrderby(pageable.getSort());
        List<E> rows = sqlSession.selectList(statement, parameters, rowBounds);
        int total = PaginationInterceptor.getPaginationTotal();
        Page<E> page = new PageImpl<E>(rows, pageable, total);
        return page;
    }

    @Override
    public <E> Page<E> findPage(String namespace, String statementId, Map<String, Object> parameters, Pageable pageable) {
        String statement = namespace + "." + statementId;
        RowBounds rowBounds = new RowBounds(pageable.getOffset(), pageable.getPageSize());
        PaginationInterceptor.setPaginationOrderby(pageable.getSort());
        List<E> rows = sqlSession.selectList(statement, parameters, rowBounds);
        int total = PaginationInterceptor.getPaginationTotal();
        Page<E> page = new PageImpl<E>(rows, pageable, total);
        return page;
    }

    @Override
    public <V> Map<String, V> findMap(String namespace, String statementId, Map<String, Object> parameters, String mapKey, Integer top) {
        String statement = namespace + "." + statementId;
        if (top != null) {
            RowBounds rowBounds = new RowBounds(0, top);
            return sqlSession.selectMap(statement, parameters, mapKey, rowBounds);
        } else {
            return sqlSession.selectMap(statement, parameters, mapKey);
        }
    }

    @Override
    public <V> Map<String, V> findMap(String namespace, String statementId, Map<String, Object> parameters, String mapKey) {
        return findMap(namespace, statementId, parameters, mapKey, null);
    }

    public void setSqlSession(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    //由于本框架优先采用Hibernate进行数据管理，因此MyBatis仅用于复杂的查询之用，不做数据更新操作以免干扰Hibernate缓存
    //    public int execute(String namespace, String statementId, Object parameter) {
    //        String statement = namespace + "." + statementId;
    //        return sqlSession.update(statement, parameter);
    //    }
}
