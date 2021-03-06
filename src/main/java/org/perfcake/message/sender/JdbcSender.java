/*
 * -----------------------------------------------------------------------\
 * PerfCake
 *  
 * Copyright (C) 2010 - 2013 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package org.perfcake.message.sender;

import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;

import org.apache.log4j.Logger;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * The sender that is able to send SQL queries via JDBC.
 * <p/>
 * TODO: Report individual result lines to result validator
 *
 * @author Pavel Macík <pavel.macik@gmail.com>
 * @author Martin Večeřa <marvenec@gmail.com>
 */
public class JdbcSender extends AbstractSender {
   /**
    * The sender's logger.
    */
   private static final Logger log = Logger.getLogger(JdbcSender.class);

   /**
    * JDBC URL string.
    */
   private String jdbcUrl = "";

   /**
    * JDBC driver class.
    */
   private String driverClass = "";

   /**
    * JDBC username.
    */
   private String username = "";

   /**
    * JDBC password.
    */
   private String password = "";

   /**
    * JDBC connection.
    */
   private Connection connection = null;

   /**
    * SQL statement.
    */
   private Statement statement;

   /*
    * (non-Javadoc)
    *
    * @see org.perfcake.message.sender.AbstractSender#init()
    */
   @Override
   public void init() throws Exception {
      this.jdbcUrl = target;
      Class.forName(driverClass);
      connection = DriverManager.getConnection(jdbcUrl, username, password);
   }

   /*
    * (non-Javadoc)
    *
    * @see org.perfcake.message.sender.AbstractSender#close()
    */
   @Override
   public void close() {
      try {
         connection.close();
      } catch (SQLException ex) {
         log.error(ex.getMessage());
      }
   }

   /*
    * (non-Javadoc)
    *
    * @see org.perfcake.message.sender.AbstractSender#preSend(org.perfcake.message.Message, java.util.Map)
    */
   @Override
   public void preSend(final Message message, final Map<String, String> properties) throws Exception {
      super.preSend(message, properties);
      statement = connection.createStatement();
   }

   /*
    * (non-Javadoc)
    *
    * @see org.perfcake.message.sender.AbstractSender#doSend(org.perfcake.message.Message, java.util.Map)
    */
   @Override
   public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {
      boolean result = statement.execute((String) message.getPayload());
      Serializable retVal;
      if (result) {
         ResultSet resultSet = statement.getResultSet();

         if (log.isDebugEnabled()) {
            ResultSetMetaData rsmd = resultSet.getMetaData();
            int columnCount = rsmd.getColumnCount();

            log.debug("Column count: " + columnCount);

            StringBuffer sb = new StringBuffer();
            for (int i = 1; i <= columnCount; i++) {
               sb.append(" ");
               sb.append(rsmd.getColumnName(i));
               sb.append(":");
               sb.append(rsmd.getColumnTypeName(i));
            }

            log.debug(sb.toString());

            log.debug("Result set's fetch size: " + resultSet.getFetchSize());
            log.debug("Going throught the result set...");
            int rowCount = 0;
            while (resultSet.next()) {
               rowCount++;
               // nop - go through whole result set
            }
            log.debug("Result set's row count: " + rowCount);
         }

         retVal = resultSet.toString();
      } else {
         retVal = statement.getUpdateCount();
      }

      return retVal;
   }

   /*
    * (non-Javadoc)
    *
    * @see org.perfcake.message.sender.AbstractSender#postSend(org.perfcake.message.Message)
    */
   @Override
   public void postSend(final Message message) throws Exception {
      super.postSend(message);
      statement.close();
   }

   /**
    * Used to read the value of jdbcUrl.
    *
    * @return The jdbcUrl.
    */
   public String getJdbcUrl() {
      return jdbcUrl;
   }

   /**
    * Sets the value of jdbcUrl.
    *
    * @param jdbcUrl
    *       The jdbcUrl to set.
    */
   public JdbcSender setJdbcUrl(final String jdbcUrl) {
      this.jdbcUrl = jdbcUrl;
      return this;
   }

   /**
    * Used to read the value of driverClass.
    *
    * @return The driverClass.
    */
   public String getDriverClass() {
      return driverClass;
   }

   /**
    * Sets the value of driverClass.
    *
    * @param driverClass
    *       The driverClass to set.
    */
   public JdbcSender setDriverClass(final String driverClass) {
      this.driverClass = driverClass;
      return this;
   }

   /**
    * Used to read the value of username.
    *
    * @return The username.
    */
   public String getUsername() {
      return username;
   }

   /**
    * Sets the value of username.
    *
    * @param username
    *       The username to set.
    */
   public JdbcSender setUsername(final String username) {
      this.username = username;
      return this;
   }

   /**
    * Used to read the value of password.
    *
    * @return The password.
    */
   public String getPassword() {
      return password;
   }

   /**
    * Sets the value of password.
    *
    * @param password
    *       The password to set.
    */
   public JdbcSender setPassword(final String password) {
      this.password = password;
      return this;
   }

}
