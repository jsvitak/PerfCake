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
package org.perfcake.message.generator;

import org.perfcake.PerfCakeConst;
import org.perfcake.message.Message;
import org.perfcake.message.MessageTemplate;
import org.perfcake.message.ReceivedMessage;
import org.perfcake.message.sender.MessageSender;
import org.perfcake.message.sender.MessageSenderManager;
import org.perfcake.reporting.MeasurementUnit;
import org.perfcake.reporting.ReportManager;
import org.perfcake.validation.ValidationManager;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Semaphore;

/**
 * <p> The sender task is a runnable class that is executing a single task of sending the message(s) from the message store using instances of {@link MessageSender} provided by message sender manager (see {@link MessageSenderManager}), receiving the message sender's response and handling the reporting and response message validation. </p> <p> It is used by the generators. </p>
 *
 * @author Pavel Macík <pavel.macik@gmail.com>
 * @author Martin Večeřa <marvenec@gmail.com>
 */
class SenderTask implements Runnable {

   /**
    * Sender task's logger.
    */
   private Logger log = Logger.getLogger(SenderTask.class);

   /**
    * Reference to a message sender manager that is providing the message senders.
    */
   private MessageSenderManager senderManager;

   /**
    * Reference to a message store where the messages are taken from.
    */
   private List<MessageTemplate> messageStore;

   /**
    * Indicates whether the message numbering is enabled or disabled.
    */
   private boolean messageNumberingEnabled;

   /**
    * Reference to a report manager.
    */
   private ReportManager reportManager;

   /**
    * A reference to the current validator manager. It is used to validate message responses.
    */
   private ValidationManager validationManager;

   /**
    * Semaphore used from the outside of SenderTask, it controls the amount of prepared tasks in a buffer.
    */
   private Semaphore semaphore;

   // limit the possibilities to construct this class
   protected SenderTask(Semaphore semaphore) {
      this.semaphore = semaphore;
   }

   private Serializable sendMessage(final MessageSender sender, final Message message, final HashMap<String, String> messageHeaders, final MeasurementUnit mu) {
      try {
         sender.preSend(message, messageHeaders);
      } catch (Exception e) {
         if (log.isEnabledFor(Level.ERROR)) {
            log.error("Exception occurred!", e);
         }
      }

      mu.startMeasure();

      Serializable result = null;
      try {
         result = sender.send(message, messageHeaders, mu);
      } catch (Exception e) {
         if (log.isEnabledFor(Level.ERROR)) {
            log.error("Exception occurred!", e);
         }
      }
      mu.stopMeasure();

      try {
         sender.postSend(message);
      } catch (Exception e) {
         if (log.isEnabledFor(Level.ERROR)) {
            log.error("Exception occurred!", e);
         }
      }

      return result;
   }

   @Override
   public void run() {
      assert messageStore != null && reportManager != null && validationManager != null && senderManager != null : "SenderTask was not properly initialized.";

      final Properties messageAttributes = new Properties();
      final HashMap<String, String> messageHeaders = new HashMap<>();
      MessageSender sender = null;
      ReceivedMessage receivedMessage = null;
      try {
         MeasurementUnit mu = reportManager.newMeasurementUnit();

         if (mu != null) {
            // only set numbering to headers if it is enabled, later there is no change to
            // filter out the headers before sending
            if (messageNumberingEnabled) {
               messageHeaders.put(PerfCakeConst.MESSAGE_NUMBER_HEADER, String.valueOf(mu.getIteration()));
               messageAttributes.setProperty(PerfCakeConst.MESSAGE_NUMBER_PROPERTY, String.valueOf(mu.getIteration()));
            }

            sender = senderManager.acquireSender();

            Iterator<MessageTemplate> iterator = messageStore.iterator();
            if (iterator.hasNext()) {
               while (iterator.hasNext()) {

                  MessageTemplate messageToSend = iterator.next();
                  Message currentMessage = messageToSend.getFilteredMessage(messageAttributes);
                  long multiplicity = messageToSend.getMultiplicity();

                  for (int i = 0; i < multiplicity; i++) {
                     receivedMessage = new ReceivedMessage(sendMessage(sender, currentMessage, messageHeaders, mu), messageToSend, currentMessage);
                     if (validationManager.isEnabled()) {
                        validationManager.addToResultMessages(receivedMessage);
                     }
                  }

               }
            } else {
               receivedMessage = new ReceivedMessage(sendMessage(sender, null, messageHeaders, mu), null, null);
               if (validationManager.isEnabled()) {
                  validationManager.addToResultMessages(receivedMessage);
               }
            }

            senderManager.releaseSender(sender); // !!! important !!!
            sender = null;

            reportManager.report(mu);
         }
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
         if (semaphore != null) {
            semaphore.release();
         }

         if (sender != null) {
            senderManager.releaseSender(sender);
         }
      }
   }

   protected void setSenderManager(final MessageSenderManager senderManager) {
      this.senderManager = senderManager;
   }

   protected void setMessageStore(final List<MessageTemplate> messageStore) {
      this.messageStore = messageStore;
   }

   protected void setMessageNumberingEnabled(final boolean messageNumberingEnabled) {
      this.messageNumberingEnabled = messageNumberingEnabled;
   }

   protected void setReportManager(final ReportManager reportManager) {
      this.reportManager = reportManager;
   }

   protected void setValidationManager(final ValidationManager validationManager) {
      this.validationManager = validationManager;
   }
}
