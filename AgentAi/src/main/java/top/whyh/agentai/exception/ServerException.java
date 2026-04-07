package top.whyh.agentai.exception;

import lombok.Getter;
import top.whyh.starter.common.result.ResultCode;

import java.io.Serial;


@Getter
public class ServerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final int code;

    public ServerException(String message) {
        super(message);
        this.code = ResultCode.INTERNAL_SERVER_ERROR.getCode();
    }

    public ServerException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServerException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}