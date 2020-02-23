package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.*;

@RestController
@RequestMapping("api/item")
public class ItemApiController {

    @Autowired
    private ItemService itemService;

    @GetMapping("{skuId}")
    public Result item(@PathVariable  Long skuId){
        Map<String,Object> result = itemService.getBySkuId(skuId);
        return Result.ok(result);
    }
}

//class MyCallable implements Callable<String> {
//    public String call (){
//        System.out.println("0000");
//        return "hello";
//    }
//
//    public static void main(String[] args) {
//        FutureTask<String> stringFutureTask = new FutureTask<>(new MyCallable());
//        new Thread(stringFutureTask).start();
//        try {
//            System.out.println(stringFutureTask.get());
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }
//        new ThreadPoolExecutor(3,5,5, TimeUnit.SECONDS)
//    }
//}
