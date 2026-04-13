resource "aws_api_gateway_rest_api" "idempotency-api" {
  name = "idempotency-api"
  endpoint_configuration {
    types = [ "REGIONAL" ]
  }
}

resource "aws_api_gateway_resource" "idempotency" {
  path_part   = "idempotency"
  parent_id   = aws_api_gateway_rest_api.idempotency-api.root_resource_id
  rest_api_id = aws_api_gateway_rest_api.idempotency-api.id
}

resource "aws_api_gateway_resource" "reserve" {
  path_part   = "reserve"
  parent_id   = aws_api_gateway_resource.idempotency.id
  rest_api_id = aws_api_gateway_rest_api.idempotency-api.id
}

resource "aws_api_gateway_resource" "complete" {
  path_part   = "complete"
  parent_id   = aws_api_gateway_resource.idempotency.id
  rest_api_id = aws_api_gateway_rest_api.idempotency-api.id
}

resource "aws_api_gateway_method" "post-reserve-method" {
  rest_api_id   = aws_api_gateway_rest_api.idempotency-api.id
  resource_id   = aws_api_gateway_resource.reserve.id
  http_method   = "POST"
  authorization = "NONE"
  request_models = {
    "application/json" = aws_api_gateway_model.AnyBodyJson.name
    }
  request_validator_id = aws_api_gateway_request_validator.request-validator.id
  request_parameters = {
    "method.request.header.Idempotency-Key" = true
    }
}

resource "aws_api_gateway_method" "post-complete-method" {
  rest_api_id   = aws_api_gateway_rest_api.idempotency-api.id
  resource_id   = aws_api_gateway_resource.complete.id
  http_method   = "POST"
  authorization = "NONE"
  request_models = {
    "application/json" = aws_api_gateway_model.AnyBodyJson.name
    }
  request_validator_id = aws_api_gateway_request_validator.request-validator.id
  request_parameters = {
    "method.request.header.Idempotency-Key" = true
    }
}

resource "aws_api_gateway_model" "AnyBodyJson" {
  rest_api_id  = aws_api_gateway_rest_api.idempotency-api.id
  name         = "AnyBodyJson"
  description  = "Any valid Json is accepted"
  content_type = "application/json"

  schema = jsonencode({
    "$schema": "http://json-schema.org/draft-04/schema#",
    "type": "object",
    "minProperties": 1,
    "additionalProperties": true
  })
}

resource "aws_api_gateway_request_validator" "request-validator" {
  name                        = "request-validator"
  rest_api_id                 = aws_api_gateway_rest_api.idempotency-api.id
  validate_request_body       = true
  validate_request_parameters = true
}

resource "aws_api_gateway_integration" "reserve-api-lambda-integration" {
  rest_api_id             = aws_api_gateway_rest_api.idempotency-api.id
  resource_id             = aws_api_gateway_resource.reserve.id
  http_method             = aws_api_gateway_method.post-reserve-method.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.idempotency-service.invoke_arn
}

resource "aws_api_gateway_integration" "complete-api-lambda-integration" {
  rest_api_id             = aws_api_gateway_rest_api.idempotency-api.id
  resource_id             = aws_api_gateway_resource.complete.id
  http_method             = aws_api_gateway_method.post-complete-method.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.idempotency-service.invoke_arn
}

resource "aws_api_gateway_deployment" "dev-deployment" {
  depends_on  = [aws_api_gateway_integration.reserve-api-lambda-integration, aws_api_gateway_integration.complete-api-lambda-integration]
  rest_api_id = aws_api_gateway_rest_api.idempotency-api.id
  # Any change done to the API should be re-deployed to take effect.
  triggers = {
    api_body_hash = sha256(jsonencode(aws_api_gateway_rest_api.idempotency-api.body))
    }
}

resource "aws_api_gateway_stage" "dev" {
  stage_name    = "dev"
  rest_api_id   = aws_api_gateway_rest_api.idempotency-api.id
  deployment_id = aws_api_gateway_deployment.dev-deployment.id
}