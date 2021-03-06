package com.trilemon.zbatis.generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class GenTable {

    //注意：静态类变量在类加载是最先执行，其次类字段实在对象初始化是执行，最后对象初始化在执行构造函数。

    /**
     * 日志记录
     */
    private final static Logger logger = LoggerFactory.getLogger(GenTable.class);
    //本次执行所有字段映射类型
    public Map<Integer, String> as = new HashMap<Integer, String>();
    private DbConn conn;
    private Settings settings;
    private List<String> allTableName;
    private String alltables;
    private Map<String, List<String>> allTablePK;


    public GenTable(DbConn dbConn) {
        conn = dbConn;
        settings = dbConn.getSettings();
        allTableName = getTableName();
        alltables = allTableName.toString().replaceAll("\\[", "'").replaceAll("\\]", "'").replaceAll(", ", "','");
        allTablePK = getAllTablePK();
    }

    /**
     * 根据表名生成表对象
     *
     * @param tableName
     * @param conf
     * @return
     */
    public TableBean prepareTableBean(String tableName, TableConfig conf) {
        if (conf == null) conf = TableConfig.DEFAULT;
        //验证及转换表名
        if (org.apache.commons.lang.StringUtils.isBlank(tableName)) return null;
        tableName = tableName.toLowerCase();
        logger.info("开始生成表对象，表名：" + tableName);
        if (!allTableName.contains(tableName)) {
            logger.error("表[" + tableName + "]不存在,不生成此表DAO层代码！");
            return null;
        }
        TableBean tableBean = new TableBean(tableName, conf);
        //设置各个列对象
        this.setTableColumn(tableBean);
        //转换Bean对象
        this.convertTableBean(tableBean);
        //校验Bean对象
        if (!checkTableBean(tableBean)) {
            return null;
        }
        logger.info("生成表对象成功，表名：" + tableName);
        return tableBean;
    }

    private boolean checkTableBean(TableBean tableBean) {
        // 校验主键是否为空
//		if (tableBean.getPkCol() == null) {
//			logger.error("表["+tableBean.getTableName()+"]自增主键不存在,不生成此表DAO层代码！");
//			return false;
//		}
        // 校验主键是否命名为id,？为啥一定要设置为id？
//		if (!"id".equals(tableBean.getPkCol().getColName())) {
//			logger.error("表["+tableBean.getTableName()+"]主键命名不是id,不生成此表DAO层代码！");
//			return false;
//		}
        // 校验字段中是否含有gmt_create或gmt_modified，此处为淘宝定制，一定要的字段
//		List<ColBean> cbList = tableBean.getColList();
//		boolean hasGmtCreate = false;
//		boolean hasGmtModified = false;
//		for(ColBean cb:cbList){
//			if("gmtCreate".equals(cb.getPropertyName())) hasGmtCreate = true;
//			if("gmtModified".equals(cb.getPropertyName())) hasGmtModified = true;
//		}
//		if(!hasGmtCreate||!hasGmtModified){
//			logger.error("表["+tableBean.getTableName()+"]没有gmt_create或gmt_modified字段,不生成此表DAO层代码！");
//			return false;
//		}
        return true;
    }

    /**
     * 转化表对象各个字段列类型
     *
     * @param tableBean
     * @return
     */
    private TableBean convertTableBean(TableBean tableBean) {
        // 设置表对象主键名为id(按源自段设置）
        if (tableBean.getPkCol() != null) {
            List<ColBean> cbs = tableBean.getPkCol();
            for (ColBean cb : cbs) {
                cb.setPropertyName(ColBean.getPropName(cb.getColName()));
//			cb.setPropertyName("id");
                cb.setMethodName(ColBean.getMethodName(cb.getPropertyName()));
                cb.setPropertyType(SqlType2Field.mapJavaType(cb.getColSQLType()));
//			cb.setHibernateType(FieldMapping.mapHibernateType(cb.getColSQLType()));
            }
        }
        // 设置表对象其他字段
        List<ColBean> ll = tableBean.getColList();
        for (Iterator<ColBean> it = ll.iterator(); it.hasNext(); ) {
            ColBean cb = it.next();
            cb.setPropertyName(ColBean.getPropName(cb.getColName()));
            cb.setMethodName(ColBean.getMethodName(cb.getPropertyName()));
            cb.setPropertyType(SqlType2Field.mapJavaType(cb.getColSQLType()));
        }
        // 将主键加入字段列表
        if (tableBean.getPkCol().size() > 0) {
            for (ColBean bean : tableBean.getPkCol()) {
                if (!tableBean.getColMap().containsKey(bean.getColName())) {
                    tableBean.getColMap().put(bean.getColName(), bean);
                    ll.add(0, bean);
                }
            }
        }
        return tableBean;
    }

    /**
     * 获取数据库中所有表名
     *
     * @return
     */
    public List<String> getTableName() {
        if (allTableName != null && !allTableName.isEmpty()) return allTableName;
        DatabaseMetaData dbmd = null;
        ResultSet rs = null;
        List<String> tableList = new ArrayList<String>();
        try {
            dbmd = conn.getDatabaseMetaData();

			/*获取所有指定表的元信息，参数(catalog,schemaPattern,tableNamePattern,types)
             * 返回5列数据,如下所示(schema,catalog,table_name,table_type,?)*/
            String[] types = {"TABLE"};    //类型，可以是"TABLE", "VIEW", "SYSTEM TABLE", "GLOBAL TEMPORARY", "LOCAL TEMPORARY", "ALIAS", "SYNONYM"
            rs = dbmd.getTables(null, null, "%", types);
            logger.info("============================获取所有表结构信息：");
            while (rs.next()) {
                tableList.add(rs.getString(3).trim());
                logger.info("Schema名[{}] " +
                        "表名[{}] " +
                        "表类型[{}] " +
                        "表注释[{}]",
                        rs.getString(1),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5));
            }
        } catch (SQLException e) {
            logger.error("获取所有指定表的元信息出错", e);
        }

        if (!Gen.tconfig.keySet().isEmpty()) {    //如果指定了表，则返回指定表
            List<String> tabs = new ArrayList<String>();
            for (String t : Gen.tconfig.keySet()) {
                if (tableList.contains(t)) {
                    tabs.add(t);
                } else {
                    logger.error("指定的表[" + t + "]不存在，不生成此表！");
                }
            }
            tableList = tabs;    //根据指定参数返回生成表
        }
        return tableList;
    }

    /**
     * 获取所有表的主键
     *
     * @return
     */
    private Map<String, List<String>> getAllTablePK() {
        HashMap<String, List<String>> map = new HashMap<String, List<String>>();
        DatabaseMetaData dbmd = null;
        ResultSet rs = null;
        try {
            dbmd = conn.getDatabaseMetaData();
            /*获取表的主键信息，参数(catalog,schemaPattern,tableName)一定要指定表名称，否则返回将出错。
             *返回6列数据,如下所示*/
            logger.info("============================获取所有表主键信息：");
            for (String t : allTableName) {
                rs = dbmd.getPrimaryKeys(null, null, t);
                List<String> list = new ArrayList<String>();
                while (rs.next()) {
                    list.add(rs.getString(4).toLowerCase());
                    logger.info("Schema名[{}] " +
                            "表名[{}] " +
                            "主键列名[{}] " +
                            "主键列序号[{}] " +
                            "主键名称[{}]",
                            rs.getString(5),
                            rs.getString(3),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6)
                    );
                }
                map.put(t.toLowerCase(), list);
            }
        } catch (SQLException e) {
            logger.error("获取所有指定表的主键信息出错", e);
        }
        return map;
    }

    /**
     * 获取所有指定表的字段
     *
     * @return
     */
    private TableBean setTableColumn(TableBean tableBean) {
        DatabaseMetaData dbmd = null;
        ResultSet rs = null;
        try {
            dbmd = conn.getDatabaseMetaData();
            /*获取所有指定表字段的元信息，参数(catalog,schemaPattern,tableNamePattern,types)
             * 返回23列数据,如下所示*/
            rs = dbmd.getColumns(null, null, tableBean.getTableName(), "%");
            while (rs.next()) {
                logger.info("表名[{}] " +
                        "列名[{}] " +
                        "列sqlType[{}] " +
                        "列typeName[{}] " +
                        "列precision[{}] " +
                        "列scale[{}] " +
                        "列isNullable[{}] " +
                        "列isNullable2[{}] " +
                        "列comment[{}] " +
                        "列defaultValue[{}] " +
                        "列isAutoIncrement[{}]",
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getString(6),
                        rs.getString(7),
                        rs.getString(9),
                        rs.getString(11),
                        rs.getString(18),
                        rs.getString(12),
                        rs.getString(13),
                        rs.getString(23)
                );
                ColBean cb = new ColBean();
                String colName = rs.getString(4);
                /**去掉列名小写cb.setColName(colName.toLowerCase());**/
                cb.setColName(colName);
                cb.setColType(rs.getString(6));    //列类型
                cb.setColComment(org.apache.commons.lang.StringUtils.isBlank(rs.getString(12)) ? cb.getColName() : rs.getString(12));    //列注释
                cb.setNullable(rs.getInt(11) == 0 ? false : true);    //是否可为空
                cb.setDefaultValue(rs.getString(13));
                cb.setPrecision(rs.getInt(7));    //字段长度
                cb.setScale(rs.getInt(9));
                cb.setAutoIncrement("YES".equals(rs.getString(23)) ? true : false);//是否自增字段
                // 设置列SQL类型
                int sqltype = rs.getInt(5);
                cb.setColSQLType(sqltype);
                if (!as.containsKey(sqltype)) {
                    as.put(sqltype, "typeName:" + rs.getString(6) + " -> className:【无】");
                }
                // 设置主键并添加进表对象,暂时没有查看排序是否正常
                List<String> pkFieldNames = allTablePK.get(tableBean.getTableName());
                for (String pkFieldName : pkFieldNames) {
                    if (colName.equalsIgnoreCase(pkFieldName)) {
                        cb.setPK(true);
                        tableBean.addPkCol(cb);
                        break;
                    } else {
                        cb.setPK(false);
                        tableBean.addColBean(cb);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("获取所有指定表的字段出错", e);
        }
        return tableBean;
    }


}
