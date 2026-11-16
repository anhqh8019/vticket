package vn.venusgiti.vticket;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.*;
import vn.venusgiti.vticket.Domain.Product;

@Service
public class ProductDetailsImages {
 public void apply(Product product,String data){
  if(data==null)return; // Older clients preserve the existing picture.
  if(data.isBlank()){product.detailImage=null;product.detailImageType=null;product.detailImageVersion=null;return;}
  try{
   int comma=data.indexOf(',');if(comma<0)throw new IllegalArgumentException();
   String prefix=data.substring(0,comma);String type=prefix.equals("data:image/png;base64")?"image/png":prefix.equals("data:image/jpeg;base64")?"image/jpeg":null;
   if(type==null)throw new IllegalArgumentException();
   byte[] bytes=Base64.getDecoder().decode(data.substring(comma+1));
   if(bytes.length==0||bytes.length>2097152)throw new IllegalArgumentException();
   try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
    var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IllegalArgumentException();var reader=readers.next();
    try{reader.setInput(input);String format=reader.getFormatName();
     if(!(type.equals("image/png")&&format.equalsIgnoreCase("png")||type.equals("image/jpeg")&&format.equalsIgnoreCase("JPEG")))throw new IllegalArgumentException();
     int w=reader.getWidth(0),h=reader.getHeight(0);if(w<1||h<1||w>6000||h>6000||(long)w*h>16000000||reader.read(0)==null)throw new IllegalArgumentException();
    }finally{reader.dispose();}
   }
   product.detailImage=bytes;product.detailImageType=type;product.detailImageVersion=UUID.randomUUID().toString();
  }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ảnh chi tiết phải là PNG/JPG hợp lệ, tối đa 2 MB, 6000 px mỗi cạnh và 16 megapixel");}
 }
}
