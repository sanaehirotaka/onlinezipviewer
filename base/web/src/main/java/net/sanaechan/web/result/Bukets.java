package net.sanaechan.web.result;

import java.util.List;

import lombok.Data;

@Data
public class Bukets implements LogicResult {

    private List<String> bukets;

    private boolean success = true;

}
