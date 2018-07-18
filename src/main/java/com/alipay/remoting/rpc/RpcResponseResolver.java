/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.remoting.rpc;

import org.slf4j.Logger;

import com.alipay.remoting.ResponseStatus;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.exception.ConnectionClosedException;
import com.alipay.remoting.exception.DeserializationException;
import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.exception.SerializationException;
import com.alipay.remoting.log.BoltLoggerFactory;
import com.alipay.remoting.rpc.exception.InvokeException;
import com.alipay.remoting.rpc.exception.InvokeSendFailedException;
import com.alipay.remoting.rpc.exception.InvokeServerBusyException;
import com.alipay.remoting.rpc.exception.InvokeServerException;
import com.alipay.remoting.rpc.exception.InvokeTimeoutException;
import com.alipay.remoting.rpc.protocol.RpcResponseCommand;
import com.alipay.remoting.util.StringUtils;

/**
 * Resolve response object from response command.
 * 
 * @author jiangping
 * @version $Id: RpcResponseResolver.java, v 0.1 2015-10-8 PM2:47:29 tao Exp $
 */
public class RpcResponseResolver {
    private static final Logger logger = BoltLoggerFactory.getLogger("RpcRemoting");

    /**
     * Analyze the response command and generate the response object.
     * 
     * @param responseCommand
     * @param addr
     * @return
     * @throws RemotingException 
     */
    public static Object resolveResponseObject(ResponseCommand responseCommand, String addr)
                                                                                            throws RemotingException {

        preProcess(responseCommand, addr);

        if (responseCommand.getResponseStatus() == ResponseStatus.SUCCESS) {
            return toResponseObject(responseCommand);
        } else {
            String msg = "Rpc invocation exception:" + responseCommand.getResponseStatus()
                         + ", the address is " + addr + ", id=" + responseCommand.getId();
            logger.warn(msg);
            if (responseCommand.getCause() != null) {
                throw new InvokeException(msg, responseCommand.getCause());
            } else {
                throw new InvokeException(msg + ", please check the server log.");
            }
        }

    }

    /**
     * 
     * @param responseCommand
     * @param addr
     * @throws RemotingException 
     */
    private static void preProcess(ResponseCommand responseCommand, String addr)
                                                                                throws RemotingException {

        RemotingException e = null;
        String msg = null;
        if (responseCommand == null) {
            msg = "Rpc invocation timeout[responseCommand null]! the address is " + addr;
            e = new InvokeTimeoutException(msg);
        } else {
            switch (responseCommand.getResponseStatus()) {
                case TIMEOUT:
                    msg = "Rpc invocation timeout[responseCommand TIMEOUT]! the address is " + addr;
                    e = new InvokeTimeoutException(msg);
                    break;
                case CLIENT_SEND_ERROR:
                    msg = "Rpc invocation send failed! the address is " + addr;
                    e = new InvokeSendFailedException(msg, responseCommand.getCause());
                    break;
                case CONNECTION_CLOSED:
                    msg = "Connection closed! the address is " + addr;
                    e = new ConnectionClosedException(msg);
                    break;
                case SERVER_THREADPOOL_BUSY:
                    msg = "Server thread pool busy! the address is " + addr + ", id="
                          + responseCommand.getId();
                    e = new InvokeServerBusyException(msg);
                    break;
                case CODEC_EXCEPTION:
                    msg = "Codec exception! the address is " + addr + ", id="
                          + responseCommand.getId();
                    e = new CodecException(msg);
                    break;
                case SERVER_SERIAL_EXCEPTION:
                    msg = "Server serialize response exception! the address is " + addr + ", id="
                          + responseCommand.getId() + ", serverSide=true";
                    e = new SerializationException(detailErrMsg(msg, responseCommand),
                        toThrowable(responseCommand), true);
                    break;
                case SERVER_DESERIAL_EXCEPTION:
                    msg = "Server deserialize request exception! the address is " + addr + ", id="
                          + responseCommand.getId() + ", serverSide=true";
                    e = new DeserializationException(detailErrMsg(msg, responseCommand),
                        toThrowable(responseCommand), true);
                    break;
                case SERVER_EXCEPTION:
                    msg = "Server exception! Please check the server log, the address is " + addr
                          + ", id=" + responseCommand.getId();
                    e = new InvokeServerException(detailErrMsg(msg, responseCommand),
                        toThrowable(responseCommand));
                    break;
                default:
                    break;
            }

        }

        if (StringUtils.isNotBlank(msg)) {
            logger.warn(msg);
        }
        if (null != e) {
            throw e;
        }
    }

    /**
     * Convert remoting response command to application response object.
     * 
     * @param responseCommand
     * @return
     * @throws CodecException 
     */
    private static Object toResponseObject(ResponseCommand responseCommand) throws CodecException {
        RpcResponseCommand response = (RpcResponseCommand) responseCommand;
        response.deserialize();
        return response.getResponseObject();
    }

    /**
     * Convert remoting response command to throwable if it is a throwable, otherwise return null.
     * @param responseCommand
     * @return
     * @throws CodecException
     */
    private static Throwable toThrowable(ResponseCommand responseCommand) throws CodecException {
        RpcResponseCommand resp = (RpcResponseCommand) responseCommand;
        resp.deserialize();
        Object ex = resp.getResponseObject();
        if (ex != null && ex instanceof Throwable) {
            return (Throwable) ex;
        }
        return null;
    }

    /**
     * Detail your error msg with the error msg returned from response command
     * @param originErrMsg
     * @param responseCommand
     * @return
     */
    private static String detailErrMsg(String originErrMsg, ResponseCommand responseCommand) {
        RpcResponseCommand resp = (RpcResponseCommand) responseCommand;
        if (StringUtils.isNotBlank(resp.getErrorMsg())) {
            return String.format("OriginErrorMsg:%s, AdditionalErrMsg:%s", originErrMsg,
                resp.getErrorMsg());
        } else {
            return String.format("OriginErrorMsg:%s, AdditionalErrMsg:null", originErrMsg);
        }
    }
}
