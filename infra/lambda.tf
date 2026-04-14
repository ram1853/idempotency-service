variable "function_name" {
  type    = string
  default = "idempotency-service"
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_partition" "current" {}

data "aws_iam_policy_document" "assume_role" {
    statement {
      effect = "Allow"

      principals {
        type        = "Service"
        identifiers = ["lambda.amazonaws.com"]
      }

      actions = ["sts:AssumeRole"]
    }
}

resource "aws_iam_policy" "cloudwatch-policy" {
  name = "idempotency-service-cloudwatch-policy"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
        {
            Action  = ["logs:CreateLogStream", "logs:PutLogEvents"]
            Effect   = "Allow"
            Resource = "arn:aws:logs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/lambda/${var.function_name}:*"
        },
        {
            Action  = ["logs:CreateLogGroup"]
            Effect   = "Allow"
            Resource = "arn:aws:logs:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:*"
        }
    ]
  })
}

# IAM Role that will be assumed by Lambda
resource "aws_iam_role" "idempotency-service-role" {
  name               = "idempotency-service-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
}

resource "aws_iam_role_policy_attachment" "attach-cloudwatch-policy" {
  role       = aws_iam_role.idempotency-service-role.name
  policy_arn = aws_iam_policy.cloudwatch-policy.arn
}

resource "aws_iam_role_policy_attachment" "attach-vpc-execution-policy" {
  role       = aws_iam_role.idempotency-service-role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_iam_role_policy_attachment" "attach-elasticache-policy" {
  role       = aws_iam_role.idempotency-service-role.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonElastiCacheFullAccess"
}

resource "aws_lambda_permission" "reserve_lambda_invoke" {
  statement_id  = "AllowExecutionFromAPIGateway-reserve"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.idempotency-service.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn = "arn:${data.aws_partition.current.partition}:execute-api:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:${aws_api_gateway_rest_api.idempotency-api.id}/*/${aws_api_gateway_method.post-reserve-method.http_method}${aws_api_gateway_resource.reserve.path}"
}

resource "aws_lambda_permission" "complete_lambda_invoke" {
  statement_id  = "AllowExecutionFromAPIGateway-complete"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.idempotency-service.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn = "arn:${data.aws_partition.current.partition}:execute-api:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:${aws_api_gateway_rest_api.idempotency-api.id}/*/${aws_api_gateway_method.post-complete-method.http_method}${aws_api_gateway_resource.complete.path}"
}

resource "aws_lambda_function" "idempotency-service" {
  function_name     = var.function_name
  role              = aws_iam_role.idempotency-service-role.arn
  runtime           = "java17"
  handler           = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  timeout           = 60
  memory_size       = 1024
  environment {
    variables = {
        logging_level_com_idempotency_service = "INFO"
        spring_cloud_function_definition      = "apiGwHandler"
        cache_ttl_seconds                     = 20
    }
  }
  filename         = "${path.module}/../worker/target/idempotency-service-0.0.1-SNAPSHOT-aws.jar"
  source_code_hash = filebase64sha256("${path.module}/../worker/target/idempotency-service-0.0.1-SNAPSHOT-aws.jar")

  vpc_config {
    subnet_ids                  = [aws_subnet.private-subnet-1.id, aws_subnet.private-subnet-2.id, aws_subnet.private-subnet-3.id]
    security_group_ids          = [aws_security_group.idempotency-service-sg.id]
    ipv6_allowed_for_dual_stack = false
  }
}
