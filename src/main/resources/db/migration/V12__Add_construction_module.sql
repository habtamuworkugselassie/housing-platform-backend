-- Migration: Add comprehensive construction module
-- This migration adds tables for construction projects, phases, material orders, inventory, and usage tracking

-- Construction Projects table
CREATE TABLE IF NOT EXISTS construction_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    property_id UUID,
    building_id UUID,
    real_estate_company_id UUID NOT NULL,
    project_manager_id UUID,
    status VARCHAR(50) NOT NULL DEFAULT 'PLANNING',
    type VARCHAR(50) NOT NULL,
    start_date DATE,
    planned_end_date DATE,
    actual_end_date DATE,
    budget NUMERIC(19, 2),
    total_cost NUMERIC(19, 2),
    currency VARCHAR(10) NOT NULL DEFAULT 'ETB',
    location_address TEXT,
    location_city VARCHAR(255),
    location_state VARCHAR(255),
    location_country VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_project_property FOREIGN KEY (property_id) REFERENCES properties(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_building FOREIGN KEY (building_id) REFERENCES buildings(id) ON DELETE SET NULL,
    CONSTRAINT fk_project_company FOREIGN KEY (real_estate_company_id) REFERENCES organizations(id),
    CONSTRAINT chk_project_status CHECK (status IN ('PLANNING', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_project_type CHECK (type IN ('NEW_CONSTRUCTION', 'RENOVATION', 'EXPANSION', 'REPAIR', 'MAINTENANCE'))
);

-- Construction Phases table
CREATE TABLE IF NOT EXISTS construction_phases (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'NOT_STARTED',
    type VARCHAR(50) NOT NULL,
    start_date DATE,
    planned_end_date DATE,
    actual_end_date DATE,
    completion_percentage INTEGER CHECK (completion_percentage >= 0 AND completion_percentage <= 100),
    budget NUMERIC(19, 2),
    actual_cost NUMERIC(19, 2),
    sequence INTEGER NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_phase_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT chk_phase_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'ON_HOLD', 'CANCELLED')),
    CONSTRAINT chk_phase_type CHECK (type IN ('SITE_PREPARATION', 'EXCAVATION', 'FOUNDATION', 'FRAMING', 'ROOFING',
        'ELECTRICAL', 'PLUMBING', 'HVAC', 'INSULATION', 'DRYWALL', 'PAINTING',
        'FLOORING', 'FINISHING', 'LANDSCAPING', 'INSPECTION', 'OTHER'))
);

-- Material Orders table
CREATE TABLE IF NOT EXISTS material_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number VARCHAR(100) NOT NULL UNIQUE,
    project_id UUID,
    supplier_id UUID NOT NULL,
    ordered_by UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    order_date DATE NOT NULL,
    expected_delivery_date DATE,
    actual_delivery_date DATE,
    subtotal NUMERIC(19, 2),
    tax_amount NUMERIC(19, 2),
    shipping_cost NUMERIC(19, 2),
    total_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'ETB',
    notes TEXT,
    delivery_address TEXT,
    delivery_city VARCHAR(255),
    delivery_state VARCHAR(255),
    delivery_country VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_order_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE SET NULL,
    CONSTRAINT fk_order_supplier FOREIGN KEY (supplier_id) REFERENCES organizations(id),
    CONSTRAINT chk_order_status CHECK (status IN ('DRAFT', 'PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED'))
);

-- Material Order Items table
CREATE TABLE IF NOT EXISTS material_order_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL,
    material_id UUID,
    material_name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    total_price NUMERIC(19, 2) NOT NULL,
    received_quantity NUMERIC(10, 2),
    brand VARCHAR(255),
    specifications TEXT,
    sequence INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES material_orders(id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_material FOREIGN KEY (material_id) REFERENCES materials(id) ON DELETE SET NULL
);

-- Material Inventory table
CREATE TABLE IF NOT EXISTS material_inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id UUID NOT NULL,
    project_id UUID,
    warehouse_location VARCHAR(255) NOT NULL,
    quantity NUMERIC(10, 2) NOT NULL,
    reserved_quantity NUMERIC(10, 2) DEFAULT 0,
    available_quantity NUMERIC(10, 2) NOT NULL,
    minimum_stock_level NUMERIC(10, 2),
    maximum_stock_level NUMERIC(10, 2),
    unit_cost NUMERIC(19, 2),
    total_value NUMERIC(19, 2),
    unit VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'IN_STOCK',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_inventory_material FOREIGN KEY (material_id) REFERENCES materials(id),
    CONSTRAINT fk_inventory_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE SET NULL,
    CONSTRAINT chk_inventory_status CHECK (status IN ('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK', 'RESERVED', 'DAMAGED'))
);

-- Material Usage table
CREATE TABLE IF NOT EXISTS material_usage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    phase_id UUID,
    material_id UUID NOT NULL,
    inventory_id UUID,
    order_id UUID,
    quantity NUMERIC(10, 2) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    unit_cost NUMERIC(19, 2),
    total_cost NUMERIC(19, 2),
    usage_date DATE NOT NULL,
    used_by UUID,
    notes TEXT,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_usage_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_usage_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE SET NULL,
    CONSTRAINT fk_usage_material FOREIGN KEY (material_id) REFERENCES materials(id),
    CONSTRAINT fk_usage_inventory FOREIGN KEY (inventory_id) REFERENCES material_inventory(id) ON DELETE SET NULL,
    CONSTRAINT fk_usage_order FOREIGN KEY (order_id) REFERENCES material_orders(id) ON DELETE SET NULL,
    CONSTRAINT chk_usage_type CHECK (type IN ('CONSTRUCTION', 'REPAIR', 'MAINTENANCE', 'WASTE', 'RETURNED'))
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_construction_projects_company ON construction_projects(real_estate_company_id);
CREATE INDEX IF NOT EXISTS idx_construction_projects_property ON construction_projects(property_id);
CREATE INDEX IF NOT EXISTS idx_construction_projects_building ON construction_projects(building_id);
CREATE INDEX IF NOT EXISTS idx_construction_projects_status ON construction_projects(status);
CREATE INDEX IF NOT EXISTS idx_construction_projects_manager ON construction_projects(project_manager_id);

CREATE INDEX IF NOT EXISTS idx_construction_phases_project ON construction_phases(project_id);
CREATE INDEX IF NOT EXISTS idx_construction_phases_status ON construction_phases(status);
CREATE INDEX IF NOT EXISTS idx_construction_phases_sequence ON construction_phases(project_id, sequence);

CREATE INDEX IF NOT EXISTS idx_material_orders_project ON material_orders(project_id);
CREATE INDEX IF NOT EXISTS idx_material_orders_supplier ON material_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_material_orders_status ON material_orders(status);
CREATE INDEX IF NOT EXISTS idx_material_orders_date ON material_orders(order_date);
CREATE INDEX IF NOT EXISTS idx_material_orders_number ON material_orders(order_number);

CREATE INDEX IF NOT EXISTS idx_material_order_items_order ON material_order_items(order_id);
CREATE INDEX IF NOT EXISTS idx_material_order_items_material ON material_order_items(material_id);

CREATE INDEX IF NOT EXISTS idx_material_inventory_material ON material_inventory(material_id);
CREATE INDEX IF NOT EXISTS idx_material_inventory_project ON material_inventory(project_id);
CREATE INDEX IF NOT EXISTS idx_material_inventory_status ON material_inventory(status);

CREATE INDEX IF NOT EXISTS idx_material_usage_project ON material_usage(project_id);
CREATE INDEX IF NOT EXISTS idx_material_usage_phase ON material_usage(phase_id);
CREATE INDEX IF NOT EXISTS idx_material_usage_material ON material_usage(material_id);
CREATE INDEX IF NOT EXISTS idx_material_usage_date ON material_usage(usage_date);
