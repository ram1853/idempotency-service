data "aws_availability_zones" "available-azs" {
  state = "available"
}

resource "aws_subnet" "private-subnet-1" {
  vpc_id     = aws_vpc.idempotency-service-vpc.id
  cidr_block = "10.0.0.0/24"
  availability_zone = data.aws_availability_zones.available-azs.names[0]

  tags = {
    Name = "private-subnet-1"
  }
}

resource "aws_subnet" "private-subnet-2" {
  vpc_id     = aws_vpc.idempotency-service-vpc.id
  cidr_block = "10.0.1.0/24"
  availability_zone = data.aws_availability_zones.available-azs.names[1]

  tags = {
    Name = "private-subnet-2"
  }
}

resource "aws_subnet" "private-subnet-3" {
  vpc_id     = aws_vpc.idempotency-service-vpc.id
  cidr_block = "10.0.2.0/24"
  availability_zone = data.aws_availability_zones.available-azs.names[2]

  tags = {
    Name = "private-subnet-3"
  }
}