package net.sanaechan.web.result;

import lombok.Data;

@Data
public class Token implements LogicResult {

    private String token;

    private boolean success = true;
}
