resource "aws_elasticache_serverless_cache" "valkey-cache" {
  engine = "valkey"
  name   = "idempotency-data"
  description              = "This cache stores the idempotency keys with their corresponding states"
  major_engine_version     = "8"
  subnet_ids               = [aws_subnet.private-subnet-1.id, aws_subnet.private-subnet-2.id, aws_subnet.private-subnet-3.id]
  security_group_ids       = [aws_security_group.idempotency-service-sg.id]
}