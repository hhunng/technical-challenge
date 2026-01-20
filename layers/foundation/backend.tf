terraform {
  backend "s3" {
    # Backend configuration provided via backend-config.tfvars
    # Key is set dynamically: -backend-config="key=dev/foundation/terraform.tfstate"
  }
}
