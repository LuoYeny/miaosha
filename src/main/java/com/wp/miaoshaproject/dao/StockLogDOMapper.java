package com.wp.miaoshaproject.dao;

import com.wp.miaoshaproject.dataobject.StockLogDO;
import org.springframework.stereotype.Repository;

@Repository
public interface StockLogDOMapper {
    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    int deleteByPrimaryKey(String stockLogId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    int insert(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    int insertSelective(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    StockLogDO selectByPrimaryKey(String stockLogId);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    int updateByPrimaryKeySelective(StockLogDO record);

    /**
     * This method was generated by MyBatis Generator.
     * This method corresponds to the database table stock_log
     *
     * @mbg.generated Tue Dec 10 17:17:52 CST 2019
     */
    int updateByPrimaryKey(StockLogDO record);
}