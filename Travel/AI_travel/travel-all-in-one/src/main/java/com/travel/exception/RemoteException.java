package com.travel.exception;

import com.travel.common.ResultCode;
import lombok.Getter;

@Getter
  public class RemoteException extends RuntimeException {
      private final int code;
      private final String serviceName;

      public RemoteException(String serviceName, String message) {
          super(message);
          this.code = ResultCode.PYTHON_SERVICE_ERROR.getCode();
          this.serviceName = serviceName;
      }

      public RemoteException(String serviceName, String message, Throwable cause) {
          super(message, cause);
          this.code = ResultCode.PYTHON_SERVICE_ERROR.getCode();
          this.serviceName = serviceName;
      }
  }