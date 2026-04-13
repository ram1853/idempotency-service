# cidr.xyz to see IP counts available for a cidr
resource "aws_vpc" "idempotency-service-vpc" {
  cidr_block           = "10.0.0.0/16"
  instance_tenancy     = "default"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "idempotency-service-vpc"
  }
}

resource "aws_security_group" "idempotency-service-sg" {
  name        = "idempotency-service-sg"
  description = "Allow communication between lambda and elasticache"
  vpc_id      = aws_vpc.idempotency-service-vpc.id

  tags = {
    Name = "idempotency-service-sg"
  }
}

resource "aws_vpc_security_group_ingress_rule" "allow_all_from_sender" {
  security_group_id            = aws_security_group.idempotency-service-sg.id
  #referenced_security_group_id = aws_security_group.idempotency-service-sg.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol                  = "-1" # -1 means "all protocols"
}

resource "aws_vpc_security_group_egress_rule" "allow_all_traffic_ipv4" {
  security_group_id = aws_security_group.idempotency-service-sg.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1" 
}