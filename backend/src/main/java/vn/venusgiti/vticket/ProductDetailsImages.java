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
 public void applyGallery(Product product,List<String> data){
  if(data.size()>3)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tối đa 3 ảnh chi tiết");
  for(int i=0;i<3;i++)apply(product,i<data.size()?data.get(i):"",i);
 }
 private void assign(Product p,int slot,byte[] bytes,String type){
  String version=bytes==null?null:UUID.randomUUID().toString();
  if(slot==0){p.detailImage=bytes;p.detailImageType=type;p.detailImageVersion=version;}
  else if(slot==1){p.detailImage2=bytes;p.detailImageType2=type;p.detailImageVersion2=version;}
  else {p.detailImage3=bytes;p.detailImageType3=type;p.detailImageVersion3=version;}
 }
 public void apply(Product product,String data){apply(product,data,0);}
 private void apply(Product product,String data,int slot){
  if(data==null)return; // Older clients preserve the existing picture.
  if(data.isBlank()){assign(product,slot,null,null);return;}
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
   assign(product,slot,bytes,type);
  }catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Ảnh chi tiết phải là PNG/JPG hợp lệ, tối đa 2 MB, 6000 px mỗi cạnh và 16 megapixel");}
 }
}
