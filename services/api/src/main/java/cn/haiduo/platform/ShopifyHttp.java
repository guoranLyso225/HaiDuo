package cn.haiduo.platform;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

public class ShopifyHttp {
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json=new ObjectMapper();
 // Limit bytes while streaming, not after allocating an arbitrary remote response.
 private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
  private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
  private Flow.Subscription subscription;private long received;
  public CompletionStage<byte[]> getBody(){return delegate.getBody();}
  public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
  public void onNext(List<ByteBuffer> buffers){for(var b:buffers)received+=b.remaining();if(received>2097152){subscription.cancel();delegate.onError(new IllegalStateException("Platform response exceeds limit"));return;}delegate.onNext(buffers);}
  public void onError(Throwable e){delegate.onError(e);}
  public void onComplete(){delegate.onComplete();}
 }
 private HttpResponse<byte[]> send(HttpRequest request)throws IOException,InterruptedException {
  var future=client.sendAsync(request,ignored->new LimitedBody());
  try{return future.get(10,TimeUnit.SECONDS);}
  catch(TimeoutException e){future.cancel(true);throw new SocketTimeoutException("Platform response deadline exceeded");}
  catch(InterruptedException e){future.cancel(true);Thread.currentThread().interrupt();throw e;}
  catch(ExecutionException e){Throwable cause=e.getCause();while(cause instanceof CompletionException&&cause.getCause()!=null)cause=cause.getCause();if(cause instanceof IOException io)throw io;if(cause instanceof RuntimeException r)throw r;throw new IOException("Platform transport failed",cause);}
 }
 public JsonNode post(URI uri,Map<String,String> headers,String body)throws Exception {
  for(int attempt=0;attempt<3;attempt++){
   var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).POST(HttpRequest.BodyPublishers.ofString(body));headers.forEach(builder::header);
   HttpResponse<byte[]> response;
   try{response=send(builder.build());}catch(IOException failure){if(attempt==2)throw failure;Thread.sleep(100L*(1<<attempt));continue;}
   boolean retry=response.statusCode()==429||response.statusCode()>=500;
   if(retry&&attempt<2){long delay=100L*(1<<attempt);var retryAfter=response.headers().firstValue("Retry-After");if(retryAfter.isPresent())try{delay=Math.min(5000,Math.max(0,Long.parseLong(retryAfter.get()))*1000);}catch(NumberFormatException ignored){}Thread.sleep(Math.max(100,delay));continue;}
   if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Platform HTTP "+response.statusCode());
   JsonNode result=json.readTree(response.body());
   if(result.has("errors")){boolean throttled=false;for(var error:result.path("errors"))if(error.path("extensions").path("code").asText().equals("THROTTLED"))throttled=true;if(throttled&&attempt<2){Thread.sleep(100L*(1<<attempt));continue;}throw new IllegalStateException("Platform GraphQL request rejected");}
   return result;
  }
  throw new IllegalStateException("Platform retries exhausted");
 }
 public JsonNode form(URI uri,Map<String,String> fields)throws Exception{String body=fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));return post(uri,Map.of("Content-Type","application/x-www-form-urlencoded","Accept","application/json"),body);}
}
