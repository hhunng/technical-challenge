# Remote state data source for Foundation layer
data "terraform_remote_state" "foundation" {
  backend = "s3"

  config = {
    bucket = local.state_bucket
    key    = "foundation/terraform.tfstate"
    region = local.aws_region
  }
}
