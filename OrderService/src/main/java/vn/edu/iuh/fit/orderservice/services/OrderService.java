package vn.edu.iuh.fit.orderservice.services;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import vn.edu.iuh.fit.orderservice.dto.InventoryResponse;
import vn.edu.iuh.fit.orderservice.dto.OrderLineItemsDto;
import vn.edu.iuh.fit.orderservice.dto.OrderRequest;
import vn.edu.iuh.fit.orderservice.event.OrderPlacedEvent;
import vn.edu.iuh.fit.orderservice.models.Order;
import vn.edu.iuh.fit.orderservice.models.OrderLineItems;
import vn.edu.iuh.fit.orderservice.repositories.OrderRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class OrderService {

    @Autowired
    private final OrderRepository orderRepository;
    @Autowired
    private final RestTemplate restTemplate;

//    private final WebClient.Builder webClientBuilder;
//    private final ObservationRegistry observationRegistry;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${inventory.service.url}")
    private String inventoryServiceUrl;

//    public OrderService(OrderRepository orderRepository, RestTemplate restTemplate, WebClient.Builder webClientBuilder, ObservationRegistry observationRegistry, ApplicationEventPublisher applicationEventPublisher) {
//        this.orderRepository = orderRepository;
//        this.restTemplate = restTemplate;
//        this.webClientBuilder = webClientBuilder;
//        this.observationRegistry = observationRegistry;
//        this.applicationEventPublisher = applicationEventPublisher;
//    }

    public String placeOrder(OrderRequest orderRequest) {
        System.out.println(orderRequest);
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());

        order.setOrderLineItemsList(orderLineItems);

        List<String> skuCodes = order.getOrderLineItemsList().stream()
                .map(OrderLineItems::getSkuCode)
                .collect(Collectors.toList());

        // Build the URL with multiple skuCodes as query parameters
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(inventoryServiceUrl + "/api/v1/inventory");
        System.out.println(skuCodes);
        skuCodes.forEach(skuCode -> builder.queryParam("skuCode", skuCode));
        String url = builder.build().toUriString();
        System.out.println(url);

        // Send GET request to inventory service
        ResponseEntity<InventoryResponse[]> responseEntity = restTemplate.getForEntity(
                url,
                InventoryResponse[].class
        );
        System.out.println(responseEntity);
        InventoryResponse[] inventoryResponseArray = responseEntity.getBody();

        // Check if all products are in stock
        boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
                .allMatch(InventoryResponse::isInStock);

        if (allProductsInStock) {
            orderRepository.save(order);
            // Publish Order Placed Event
            applicationEventPublisher.publishEvent(new OrderPlacedEvent(this, order.getOrderNumber()));
            return "Order Placed";
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

//    public String placeOrder(OrderRequest orderRequest) {
//        Order order = new Order();
//        order.setOrderNumber(UUID.randomUUID().toString());
//
//        List<OrderLineItems> orderLineItems = orderRequest.getOrderLineItemsDtoList()
//                .stream()
//                .map(this::mapToDto)
//                .toList();
//
//        order.setOrderLineItemsList(orderLineItems);
//
//        List<String> skuCodes = order.getOrderLineItemsList().stream()
//                .map(OrderLineItems::getSkuCode)
//                .toList();
//
//        // Call Inventory Service, and place order if product is in
//        // stock
//        Observation inventoryServiceObservation = Observation.createNotStarted("inventory-service-lookup",
//                this.observationRegistry);
//        inventoryServiceObservation.lowCardinalityKeyValue("call", "inventory-service");
//        return inventoryServiceObservation.observe(() -> {
//            InventoryResponse[] inventoryResponseArray = webClientBuilder.build().get()
//                    .uri("http://inventory-service/api/v1/inventory",
//                            uriBuilder -> uriBuilder.queryParam("skuCode", skuCodes).build())
//                    .retrieve()
//                    .bodyToMono(InventoryResponse[].class)
//                    .block();
//
//            boolean allProductsInStock = Arrays.stream(inventoryResponseArray)
//                    .allMatch(InventoryResponse::isInStock);
//
//            if (allProductsInStock) {
//                orderRepository.save(order);
//                // publish Order Placed Event
//                applicationEventPublisher.publishEvent(new OrderPlacedEvent(this, order.getOrderNumber()));
//                return "Order Placed";
//            } else {
//                throw new IllegalArgumentException("Product is not in stock, please try again later");
//            }
//        });
//
//    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }
}
