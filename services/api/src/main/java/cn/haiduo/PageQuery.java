package cn.haiduo;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public record PageQuery(int page,int size){public PageQuery{if(page<1||page>1000000||size<1||size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");}public int offset(){return (page-1)*size;}}
